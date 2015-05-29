/* jniTofmu.c */

#include <jni.h>
#include <stdio.h>
#ifdef _MSC_VER
#include<windows.h>
#else
#include <dlfcn.h>
#endif
#include "fmi.h"
#include <string.h>
#include <time.h>
#include <stdint.h>

typedef fmiComponent (*importFunctionInstantiate)(fmiString, fmiType, fmiString,
	fmiString, const fmiCallbackFunctions*, fmiBoolean, fmiBoolean);
typedef fmiStatus (*importFunctionSetupExperiment)(fmiComponent, fmiBoolean,
	fmiReal, fmiReal, fmiBoolean, fmiReal);
typedef fmiStatus (*importFunctionSetTime)(fmiComponent, fmiReal);
typedef fmiStatus (*importFunctionEnterInitializationMode)(fmiComponent);
typedef fmiStatus (*importFunctionExitInitializationMode)(fmiComponent);
typedef fmiStatus (*importFunctionNewDiscreteStates)(fmiComponent,
	fmiEventInfo*);
typedef fmiStatus (*importFunctionEnterContinuousTimeMode)(fmiComponent);
typedef fmiStatus (*importFunctionCompletedIntegratorStep)(fmiComponent,
	fmiBoolean, fmiBoolean*, fmiBoolean*);
typedef fmiStatus (*importSetContinuousStates)(fmiComponent, const fmiReal[],
	size_t);
typedef fmiStatus (*importGetDerivatives)(fmiComponent, fmiReal[], size_t);
typedef fmiStatus (*importGetContinuousStates)(fmiComponent, fmiReal[], size_t);
typedef fmiStatus (*importGetReal)(fmiComponent, const fmiValueReference[],
	size_t, fmiReal[]);
typedef fmiStatus (*importSetReal)(fmiComponent, const fmiValueReference[],
	size_t, const fmiReal[]);
typedef fmiStatus (*importTerminate)(fmiComponent); // Unused FMI Function.
typedef void (*importFreeInstance)(fmiComponent);   //  Unused FMI Function.
typedef fmiStatus (*importGetDirectionalDerivative)(fmiComponent, 
	const fmiValueReference[], size_t, const fmiValueReference[], 
	size_t, fmiReal[], fmiReal[]);

typedef struct idfFmu_t {
	// FMU parameters
	int index;								// FMU instance index
	int nx;                          		// number of state variables
	double *x;                       		// continuous states
	double *xdot;              	            // the corresponding derivatives in same order
	fmiEventInfo eventInfo;                 // updated by calls to initialize and eventUpdate
	fmiBoolean stepEvent;					// flag for stepEvent
	fmiBoolean terminateSimulation;			// flag to terminate simulation
	const char* guid;                		// global unique id of the fmu
	fmiCallbackFunctions callbacks;  	    // called by the model during simulation
	fmiStatus fmiFlag;               		// return code of the fmu functions
	fmiReal startTime;              		// start time
	fmiReal stopTime;                		// end time
	double prevTime;						// last simulation time
	fmiBoolean toleranceDefined;            // true if model description define tolerance
	fmiReal tolerance;           			// used in setting up the experiment
	fmiBoolean visible;   					// no simulator user interface
	const char *instanceName;        		// instance name
	const char* fmuResourceLocation;		// FMU resource location
	const char* fmuNativeLibraryLocation;	// FMU native library path
	void *handle;							// library handle
	fmiBoolean loggingOn;					// loggingOn
	fmiBoolean wrapup;					    // loggingOn
	fmiComponent c; 						// instance of the fmu
	JNIEnv * env;							// JINEnv structure
	// FMI Functions
	importFunctionInstantiate instantiate;
	importFunctionSetupExperiment setupExperiment;
	importFunctionSetTime setTime;
	importFunctionEnterInitializationMode enterInitialization;
	importFunctionExitInitializationMode exitInitialization;
	importFunctionNewDiscreteStates newDiscreteStates;
	importFunctionEnterContinuousTimeMode enterContinuousTimeMode;
	importFunctionCompletedIntegratorStep completedIntegratorStep;
	importSetContinuousStates setContinuousStates;
	importGetDerivatives getDerivatives;
	importGetContinuousStates getContinuousStates;
	importGetReal getReal;
	importSetReal setReal;
	importTerminate terminate;
	importFreeInstance freeInstance;
	importGetDirectionalDerivative getDirectionalDerivative;
} fmu_t;

// Global variables
static int insNum = 0;
static int arrsize = 0;
static fmu_t **fmuInstances;
static int fmuLocCoun = 0;
int ncallsDerivs = 0;
int ncallsCompDerivs = 0;
int ncallsContStates = 0;
int ncallsSetInputs = 0;
int ncallsGetOutputs = 0;
uint64_t sumTimeComputeDerivatives = 0;
uint64_t sumTimeElapsedDerivatives = 0;
uint64_t sumTimeElapsedContStates = 0;
uint64_t sumTimeElapsedSetInputs = 0;
uint64_t sumTimeElapsedGetOutputs = 0;
// FIXME: A delta of 10 will not work with the model with 18 FMUs.
// There seems to be a problem when increasing memory dynamically.
#define DELTA 10000
#define MAX_MSG_SIZE 1000

////////////////////////////////////////////////////////////////////////////////////
/// Measure execution time in nanoseconds
///
///\param timeA_p The start time.
///\param timeB_p The stop time.
////////////////////////////////////////////////////////////////////////////////////
//int64_t timespecDiff(struct timespec *timeA_p, struct timespec *timeB_p) {
//	return ((timeA_p->tv_sec * 1000000000) + timeA_p->tv_nsec)
//			- ((timeB_p->tv_sec * 1000000000) + timeB_p->tv_nsec);
//}

////////////////////////////////////////////////////////////////////////////////////
/// create a list of pointer to FMUs
///
///\param s The Pointer to FMU.
////////////////////////////////////////////////////////////////////////////////////

static void addfmuInstances(fmu_t* s) {
	fmu_t **temp;
	if (fmuLocCoun == arrsize) {
		temp = (fmu_t**) malloc(sizeof(fmu_t*) * (DELTA + arrsize));
		arrsize += DELTA;
		memcpy(temp, fmuInstances, fmuLocCoun);
		free(fmuInstances);
		fmuInstances = temp;
	}
	fmuInstances[fmuLocCoun++] = s;
}

////////////////////////////////////////////////////////////////////////////////////
/// Convert status to string
///
///\param status The FMU status.
////////////////////////////////////////////////////////////////////////////////////
static const char* fmiStatusToString(fmiStatus status) {
	switch (status) {
	case fmiOK:
		return "ok";
	case fmiWarning:
		return "warning";
	case fmiDiscard:
		return "discard";
	case fmiError:
		return "error";
	case fmiFatal:
		return "fatal";
#ifdef FMI_COSIMULATION
	case fmiPending: return "fmiPending";
#endif
	default:
		return "?";
	}
}

///////////////////////////////////////////////////////////////////////////////
/// Close specific shared library
///
///\param idx FMU instance index.
//////////////////////////////////////////////////////////////////////////////
static void freeLib(int idx) {
#ifdef _MSC_VER	
	FreeLibrary(fmuInstances[idx]->handle);
#else
	dlclose(fmuInstances[idx]->handle);
#endif
}

////////////////////////////////////////////////////////////////////////////////////
/// Wrapup simulation
///
///\param idx The FMU instance index.
////////////////////////////////////////////////////////////////////////////////////
static void wrapup(int idx) {
	// Free the FMU
	if (fmuInstances[idx]->wrapup == fmiTrue) {
		/*

		printf(
		"This is the number of compute derivatives calls: %d, the total time in ns %llu, "
		"and the average time in ns: %f\n", ncallsCompDerivs - 1,
		sumTimeComputeDerivatives,
		(double) (sumTimeComputeDerivatives / ncallsCompDerivs));

		if (ncallsSetInputs > 0) {
		printf(
		"This is the number of setReal() calls: %d, the total time in ns %llu, "
		"and the average time in ns: %f\n", ncallsSetInputs - 1,
		sumTimeElapsedSetInputs,
		(double) (sumTimeElapsedSetInputs / ncallsSetInputs));
		}
		if (ncallsGetOutputs > 0) {
		printf(
		"This is the number of getReal() calls: %d, the total time in ns %llu, "
		"and the average time in ns: %f\n",
		ncallsGetOutputs - 1, sumTimeElapsedGetOutputs,
		(double) (sumTimeElapsedGetOutputs / ncallsGetOutputs));
		}
		*/
		if (fmuInstances[idx]->c != NULL) {
			//FIXME: what should we use to release the FMU?
			//free(fmuInstances[idx]->c);
			// freeInstance seem to work on Windows 
			// It doesn't seem to work on linux
			// If there is a crash then we will have to
			// selectively use freeInstance on Windows 
			// and free on Linux to release the FMU.
			// freeInstance seems to be sensitive the the license which 
			// was used to export the FMUs.
			fmuInstances[idx]->freeInstance (fmuInstances[idx]->c);
			fmuInstances[idx]->c = NULL;
		}
		if (fmuInstances[idx]->x != NULL) {
			free(fmuInstances[idx]->x);
			fmuInstances[idx]->x = NULL;
		}
		if (fmuInstances[idx]->xdot != NULL) {
			free(fmuInstances[idx]->xdot);
			fmuInstances[idx]->xdot = NULL;
		}
		if (fmuInstances[idx]->handle != NULL){
			// Free library
			freeLib (idx);
		}
		insNum = 0;
		fmuLocCoun = 0;
		arrsize = 0;
		fmuInstances[idx]->wrapup = fmiFalse;
	} else {
		return;
	}
}

////////////////////////////////////////////////////////////////////////////////////
/// Print error message
///
///\param message The error message.
////////////////////////////////////////////////////////////////////////////////////
static int error(const char* message) {
	printf("%s\n", message);
	return 0;
}

////////////////////////////////////////////////////////////////////////////////////
/// Print FMU logging
///
///\param componentEnvironment The FMU component environment.
///\param instance The FMU instance name.
///\param status The FMU status.
///\param category The FMU logging category.
///\param message The FMU logging message.
////////////////////////////////////////////////////////////////////////////////////
static void fmuLogger(void *componentEnvironment, fmiString instance,
	fmiStatus status, fmiString category, fmiString message, ...) {
		char msg[MAX_MSG_SIZE];
		char* copy;
		va_list argp;

		// replace C format strings
		va_start(argp, message);
		vsprintf(msg, message, argp);

		// replace e.g. ## and #r12#
		copy = strdup(msg);
		//replaceRefsInMessage(copy, msg, MAX_MSG_SIZE, &fmu);
		free(copy);

		// print the final message
		if (!instance)
			instance = "?";
		if (!category)
			category = "?";
		printf("%s %s (%s): %s\n", fmiStatusToString(status), instance, category,
			msg);
		va_end(argp);
}

////////////////////////////////////////////////////////////////////////////////////
/// Set FMU inputs
///
///\param instance The FMU instance index.
///\param invals The FMU input values.
///\param invalrefs The FMU value references.
////////////////////////////////////////////////////////////////////////////////////
static fmiStatus jsetReal(int idx, jdoubleArray invals, jlongArray invalrefs) {
	// set the inputs
	int n_uu = (int) (*fmuInstances[idx]->env)->GetArrayLength(
		fmuInstances[idx]->env, invals);
	int i;
	fmiReal* uu = (fmiReal *) calloc(n_uu, sizeof(fmiReal));
	fmiValueReference* uu_ref = (fmiValueReference *) calloc(n_uu,
		sizeof(fmiValueReference));
	jdouble *tmp_invals = (*fmuInstances[idx]->env)->GetDoubleArrayElements(
		fmuInstances[idx]->env, invals, 0);
	jlong *tmp_invalrefs = (*fmuInstances[idx]->env)->GetLongArrayElements(
		fmuInstances[idx]->env, invalrefs, 0);

	for (i = 0; i < n_uu; i++) {
		uu_ref[i] = (fmiValueReference) tmp_invalrefs[i];
		uu[i] = (fmiReal) tmp_invals[i];
	}

	/////////////////////////////////////////
	// Measure time if comments disabled.
	//struct timespec start, end;
	//clock_gettime(CLOCK_MONOTONIC, &start);
	fmuInstances[idx]->fmiFlag = fmuInstances[idx]->setReal(
		fmuInstances[idx]->c, uu_ref, n_uu, uu);
	//clock_gettime(CLOCK_MONOTONIC, &end);
	//uint64_t timeElapsed = timespecDiff(&end, &start);
	//ncallsSetInputs++;
	//sumTimeElapsedSetInputs = sumTimeElapsedSetInputs + timeElapsed;
	/////////////////////////////////////////

	if (fmuInstances[idx]->fmiFlag > fmiWarning) {
		// release array
		(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
			fmuInstances[idx]->env, invals, tmp_invals, 0);
		(*fmuInstances[idx]->env)->ReleaseLongArrayElements(
			fmuInstances[idx]->env, invalrefs, tmp_invalrefs, 0);
		free(uu);
		free(uu_ref);
		return error("could not set continuous states");
	}

	// release array
	(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
		fmuInstances[idx]->env, invals, tmp_invals, 0);
	(*fmuInstances[idx]->env)->ReleaseLongArrayElements(fmuInstances[idx]->env,
		invalrefs, tmp_invalrefs, 0);
	free(uu);
	free(uu_ref);
	return fmuInstances[idx]->fmiFlag;
}

////////////////////////////////////////////////////////////////////////////////////
/// Set FMU continuous states
///
///\param instance The FMU instance index.
///\param invals The FMU continuous states values.
////////////////////////////////////////////////////////////////////////////////////
static fmiStatus jsetContinuousStates(int idx, jdoubleArray invals) {
	// set the continuous states
	int n_xx = fmuInstances[idx]->nx;
	int i;
	fmiReal* xx = (fmiReal *) calloc(n_xx, sizeof(fmiReal));
	jdouble *tmp_invals = (*fmuInstances[idx]->env)->GetDoubleArrayElements(
		fmuInstances[idx]->env, invals, 0);
	for (i = 0; i < n_xx; i++) {
		xx[i] = (fmiReal) tmp_invals[i];
	}

	/////////////////////////////////////////
	// Measure time if comments disabled.
	//struct timespec start, end;
	//clock_gettime(CLOCK_MONOTONIC, &start);
	fmuInstances[idx]->fmiFlag = fmuInstances[idx]->setContinuousStates(
		fmuInstances[idx]->c, xx, fmuInstances[idx]->nx);
	//clock_gettime(CLOCK_MONOTONIC, &end);
	//uint64_t timeElapsed = timespecDiff(&end, &start);
	//ncallsContStates++;
	//sumTimeElapsedContStates = sumTimeElapsedContStates + timeElapsed;
	/////////////////////////////////////////

	if (fmuInstances[idx]->fmiFlag > fmiWarning) {
		// release array
		(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
			fmuInstances[idx]->env, invals, tmp_invals, 0);
		free(xx);
		return error("could not set continuous states");
	}

	// release array
	(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
		fmuInstances[idx]->env, invals, tmp_invals, 0);
	free(xx);
	return fmuInstances[idx]->fmiFlag;
}

////////////////////////////////////////////////////////////////////////////////////
/// Get FMU continuous states
///
///\param instance The FMU instance index.
///\param x_get The FMU continuous states values.
////////////////////////////////////////////////////////////////////////////////////
static fmiStatus jgetContinuousStates(int idx, jdoubleArray x_get) {
	int i;
	// get the continuous states
	jdouble *tmp_x_get = (*fmuInstances[idx]->env)->GetDoubleArrayElements(
		fmuInstances[idx]->env, x_get, 0);
	fmuInstances[idx]->fmiFlag = fmuInstances[idx]->getContinuousStates(
		fmuInstances[idx]->c, fmuInstances[idx]->x, fmuInstances[idx]->nx);
	if (fmuInstances[idx]->fmiFlag > fmiWarning) {
		// release array
		(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
			fmuInstances[idx]->env, x_get, tmp_x_get, 0);
		return error("could not retrieve states");
	}

	// set the values back to caller
	for (i = 0; i < fmuInstances[idx]->nx; i++) {
		tmp_x_get[i] = fmuInstances[idx]->x[i];
	}
	// release array
	(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
		fmuInstances[idx]->env, x_get, tmp_x_get, 0);
	return fmuInstances[idx]->fmiFlag;
}

////////////////////////////////////////////////////////////////////////////////////
/// Get FMU continuous derivative states
///
///\param instance The FMU instance index.
///\param x_get The FMU continuous state derivatives values.
////////////////////////////////////////////////////////////////////////////////////
static fmiStatus jgetDerivatives(int idx, jdoubleArray x_get) {
	int i;
	// get the states derivatives
	jdouble *tmp_x_get = (*fmuInstances[idx]->env)->GetDoubleArrayElements(
		fmuInstances[idx]->env, x_get, 0);

	/////////////////////////////////////////
	// Measure time if comments disabled.
	//struct timespec start, end;
	//clock_gettime(CLOCK_MONOTONIC, &start);
	fmuInstances[idx]->fmiFlag = fmuInstances[idx]->getDerivatives(
		fmuInstances[idx]->c, fmuInstances[idx]->xdot,
		fmuInstances[idx]->nx);
	//clock_gettime(CLOCK_MONOTONIC, &end);
	//uint64_t timeElapsed = timespecDiff(&end, &start);
	//ncallsDerivs++;
	//sumTimeElapsedDerivatives = sumTimeElapsedDerivatives + timeElapsed;
	/////////////////////////////////////////

	if (fmuInstances[idx]->fmiFlag > fmiWarning) {
		// release array
		(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
			fmuInstances[idx]->env, x_get, tmp_x_get, 0);
		return error("could not retrieve state derivatives");
	}

	// set the values back to caller
	for (i = 0; i < fmuInstances[idx]->nx; i++) {
		tmp_x_get[i] = fmuInstances[idx]->xdot[i];
	}
	// release array
	(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
		fmuInstances[idx]->env, x_get, tmp_x_get, 0);

	return fmuInstances[idx]->fmiFlag;
}


////////////////////////////////////////////////////////////////////////////////////
/// Galculate the second derivative with respect to dependent iputs
///
///\param instance The FMU instance index.
///\param tmp_xx The FMU dependent variables.
///\param tmp_xx_refs The FMU dependent value references.
///\param x_dot_get_ref The FMU state derivative.
////////////////////////////////////////////////////////////////////////////////////
static double jcalcJac(int idx, int n_xx, double tmp_xx [], jlong tmp_xx_refs [], jlong x_dot_get_ref) {
	double sum = 0.0;
	int i;
	fmiReal* xx_dot = (fmiReal *) calloc(n_xx, sizeof(fmiReal));	
	fmiValueReference vKnownRef [1];
	const fmiValueReference vUnknownRef[1] =  {(fmiValueReference) x_dot_get_ref};
	fmiReal dvKnown[1]  = {1.0}; 
	fmiReal dvUnknown[1]; 
	for (i = 0; i < n_xx; i++) {
		xx_dot[i] = (fmiReal) tmp_xx[i];
		vKnownRef[0] =  (fmiValueReference) tmp_xx_refs[i];
		// FIXME: The calling order of _fmiGetDirectionalDerivative is not according to the 
        // Standard. This was modified to accommodate Dymola 2015 FD01's FMUs which have a wron calling
        // order. Dassault Systems was informed and will fix this in Dymola 2016.
        // The current calling order is:  
        //fmuInstances[idx]->fmiFlag = fmuInstances[idx]->getDirectionalDerivative(fmuInstances[idx]->c, vKnownRef, 1, vUnKnownRef, 1, dvKnown, dvUnknown);
        // The correct calling order should be:
        //fmuInstances[idx]->fmiFlag = fmuInstances[idx]->getDirectionalDerivative(fmuInstances[idx]->c, vUnknownRef, 1, vKnownRef, 1, dvKnown, dvUnknown);
		fmuInstances[idx]->fmiFlag = fmuInstances[idx]->getDirectionalDerivative(fmuInstances[idx]->c, vKnownRef, 1, vUnknownRef, 1, dvKnown, dvUnknown);
		if (fmuInstances[idx]->fmiFlag > fmiWarning) {
			error("could not retrieve directional state derivatives for FMU instance.");
			return -1;
		}
		sum = sum + dvUnknown [0] * xx_dot[i];
	}
	// Free the allocated arrays.
	free (xx_dot);
	return sum;
}

////////////////////////////////////////////////////////////////////////////////////
/// Get FMU continuous derivative states
///
///\param instance The FMU instance index.
///\param x_get The FMU continuous state derivatives values.
////////////////////////////////////////////////////////////////////////////////////

static fmiStatus jgetDirectionalDerivative(int idx, jlong x_dot_get_ref, 
	jdoubleArray xx_xdot_get, jlongArray xx_xdot_get_refs, 
	jdoubleArray uu_xdot_get, jlongArray uu_xdot_get_refs, 
	jdoubleArray xdot_dot_get) {
		int n_uu, n_xx, n_xxdot;
		double jac_uu, jac_xx;
		jdouble *tmp_xdot_dot_get = (*fmuInstances[idx]->env)->GetDoubleArrayElements(
			fmuInstances[idx]->env, xdot_dot_get, 0);

		// get the dependent state
		jdouble *tmp_xx_xdot_get = (*fmuInstances[idx]->env)->GetDoubleArrayElements(
			fmuInstances[idx]->env, xx_xdot_get, 0);

		// get the dependent inputs derivatives
		jdouble *tmp_uu_xdot_get = (*fmuInstances[idx]->env)->GetDoubleArrayElements(
			fmuInstances[idx]->env, uu_xdot_get, 0);

		// get the value reference of dependent states derivatives
		jlong *tmp_xx_xdot_get_refs = (*fmuInstances[idx]->env)->GetLongArrayElements(
			fmuInstances[idx]->env, xx_xdot_get_refs, 0);

		// get the value references of dependent inputs
		jlong *tmp_uu_xdot_get_refs = (*fmuInstances[idx]->env)->GetLongArrayElements(
			fmuInstances[idx]->env, uu_xdot_get_refs, 0);

		// Get the dependent inputs and set them
		n_uu = (int) (*fmuInstances[idx]->env)->GetArrayLength(
			fmuInstances[idx]->env, uu_xdot_get);
		jac_uu = jcalcJac (idx, n_uu, tmp_uu_xdot_get, tmp_uu_xdot_get_refs, x_dot_get_ref);

		// Get the dependent states and set them
		n_xx = (int) (*fmuInstances[idx]->env)->GetArrayLength(
			fmuInstances[idx]->env, xx_xdot_get);
		jac_xx = jcalcJac (idx, n_xx, tmp_xx_xdot_get, tmp_xx_xdot_get_refs, x_dot_get_ref);

		// Get the second derivative
		tmp_xdot_dot_get[0] = jac_uu + jac_xx;

		// release array
		// FIXME: Need better error handling for case wehre we don't get second derivatives.
		(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
			fmuInstances[idx]->env, xx_xdot_get, tmp_xx_xdot_get, 0);

		(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
			fmuInstances[idx]->env, uu_xdot_get, tmp_uu_xdot_get, 0);

		(*fmuInstances[idx]->env)->ReleaseLongArrayElements(
			fmuInstances[idx]->env, xx_xdot_get_refs, tmp_xx_xdot_get_refs, 0);

		(*fmuInstances[idx]->env)->ReleaseLongArrayElements(
			fmuInstances[idx]->env, uu_xdot_get_refs, tmp_uu_xdot_get_refs, 0);

		(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(fmuInstances[idx]->env,
			xdot_dot_get, tmp_xdot_dot_get, 0);

		return fmuInstances[idx]->fmiFlag;
}


////////////////////////////////////////////////////////////////////////////////////
/// Get FMU outputs
///
///\param instance The FMU instance index.
///\param x_get The FMU output values.
///\param x_get_ref The FMU output value references.
////////////////////////////////////////////////////////////////////////////////////
static fmiStatus jgetReal(int idx, jdoubleArray x_get, jdoubleArray x_get_ref) {
	int i;
	// get the continuous states
	jdouble *tmp_x_get = (*fmuInstances[idx]->env)->GetDoubleArrayElements(
		fmuInstances[idx]->env, x_get, 0);
	jlong *tmp_x_get_ref = (*fmuInstances[idx]->env)->GetLongArrayElements(
		fmuInstances[idx]->env, x_get_ref, 0);
	int n_oo = (int) (*fmuInstances[idx]->env)->GetArrayLength(
		fmuInstances[idx]->env, x_get);

	fmiReal* oo = (fmiReal *) calloc(n_oo, sizeof(fmiReal));
	fmiValueReference* oo_ref = (fmiValueReference *) calloc(n_oo,
		sizeof(fmiValueReference));
	for (i = 0; i < n_oo; i++) {
		oo_ref[i] = (fmiValueReference) tmp_x_get_ref[i];
	}

	/////////////////////////////////////////
	// Measure time if comments disabled.
	//struct timespec start, end;
	//clock_gettime(CLOCK_MONOTONIC, &start);
	fmuInstances[idx]->fmiFlag = fmuInstances[idx]->getReal(
		fmuInstances[idx]->c, oo_ref, n_oo, oo);
	//clock_gettime(CLOCK_MONOTONIC, &end);
	//uint64_t timeElapsed = timespecDiff(&end, &start);
	//ncallsGetOutputs++;
	//sumTimeElapsedGetOutputs = sumTimeElapsedGetOutputs + timeElapsed;
	/////////////////////////////////////////

	if (fmuInstances[idx]->fmiFlag > fmiWarning) {
		// release array
		(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
			fmuInstances[idx]->env, x_get, tmp_x_get, 0);
		(*fmuInstances[idx]->env)->ReleaseLongArrayElements(
			fmuInstances[idx]->env, x_get_ref, tmp_x_get_ref, 0);
		free(oo);
		free(oo_ref);
		return error("could not retrieve outputs");
	}

	for (i = 0; i < n_oo; i++) {
		tmp_x_get[i] = oo[i];
	}

	// release array
	(*fmuInstances[idx]->env)->ReleaseDoubleArrayElements(
		fmuInstances[idx]->env, x_get, tmp_x_get, 0);
	(*fmuInstances[idx]->env)->ReleaseLongArrayElements(fmuInstances[idx]->env,
		x_get_ref, tmp_x_get_ref, 0);
	free(oo);
	free(oo_ref);
	return fmuInstances[idx]->fmiFlag;
}

///////////////////////////////////////////////////////////////////////////////
/// Get address of specific function from specific shared library
///
///\param name Function name.
///\return Address of the specific function.
//////////////////////////////////////////////////////////////////////////////
static void* getAdr(int idx, const char* name) {
	void* fp;
	char *buf;
	buf = (char*)malloc(strlen(name)+4);
	sprintf (buf, "%s%s", "fmi2", name);
#ifdef _MSC_VER	
	fp = GetProcAddress(fmuInstances[idx]->handle, buf);
	if (!fp){
		//FIXME: Free the buffer causes the code to crash?
		// free (buf);
		buf = (char*)malloc(strlen(name)+3);
		sprintf (buf, "%s%s", "fmi", name);
		fp = GetProcAddress(fmuInstances[idx]->handle, buf);
		if (!fp) {
			printf("Error: Function %s not found in FMI functions library\n", buf);
		}
		//FIXME: Free the buffer causes the code to crash?
		//free (buf);
		return fp;
	}
#else
	fp = dlsym(fmuInstances[idx]->handle, buf);
	if (!fp){
		//FIXME: Free the buffer causes the code to crash?
		// free (buf);
		buf = (char*)malloc(strlen(name)+3);
		sprintf (buf, "%s%s", "fmi", name);
		fp = dlsym(fmuInstances[idx]->handle, buf);
		if (!fp) {
			printf("Error: Function %s not found in FMI functions library\n", buf);
		}
		// FIXME: Free the buffer causes the code to crash?
		// free (buf);
		return fp;
	}
#endif
	if (!fp) {
		printf("Error: Function %s not found in FMI functions library\n", buf);
	}
	// FIXME: Free the buffer causes the code to crash?
	// free (buf);
	return fp;
}

///////////////////////////////////////////////////////////////////////////////
/// Load specific shared library
///
///\param idx FMU instance index.
//////////////////////////////////////////////////////////////////////////////
static int loadLib(int idx) {
#ifdef _MSC_VER	
	fmuInstances[idx]->handle = LoadLibrary(
		fmuInstances[idx]->fmuNativeLibraryLocation);
#else
	fmuInstances[idx]->handle = dlopen(
		fmuInstances[idx]->fmuNativeLibraryLocation, RTLD_LAZY);
#endif
	if (fmuInstances[idx]->handle == NULL) {
		return -1;
	}
	return 0;
}


///////////////////////////////////////////////////////////////////////////////
/// FMU native function to interface with JAVA
///
///\param name Function name.
/// \param idx Index of the FMU instance.
/// \param fla Flag to determine the FMI functions to call.
///            0: Instantiate.
///            1: Initialize. 
///            2: Enter event mode. 
///            3: Enter continuous mode.
///            4: Get continuous states.
///            5: Get state derivatives.
///            6: Set continuous states.
///            7: Set single inputs.
///            8: Get single outputs.
///            9: Complete integrator.
///            -1: Terminate simulation.
///            -10: Get directional derivatives.
/// \param instance FMU instance name.
/// \param pathLib Path to the FMU native library.
/// \param pathres Path to the FMU resource location.
/// \param tStart FMU simulation start time.
/// \param tEnd FMU simulation end time.
/// \param time Current simulation time.
/// \param toleranceDefined toleranceDefined.
/// \param tolerance FMU solver tolerance.
/// \param visible FMU visible flag.
/// \param loggingOn FMU logginOn flag.
/// \param guid FMU GUID.
/// \param xdot_get  FMU state derivatives to get.
/// \param x_get FMU continuous states to get.
/// \param x_set FMU continuous states to set.
/// \param invals FMU input values.
/// \param invalrefs FMU input value references.
/// \param outvals FMU output values.
/// \param outvalrefs FMU output value references
/// \param x_dot_get_ref The FMU state derivative value reference.
/// \param xx_xdot_get The FMU directional state derivatives.
/// \param xx_xdot_get_refs The FMU directional state derivatives value references.
/// \param uu_xdot_get The FMU directional input derivatives.
/// \param uu_xdot_get_refs The FMU directional input derivatives value references.
/// \param xdot_dot_get The FMU second derivatives.
/// \return 0 if succes.
//////////////////////////////////////////////////////////////////////////////
JNIEXPORT int Java_ptolemy_actor_lib_fmi_FMUImportJNI_runNativeFMU(JNIEnv * env,
	jobject obj, jint idx, jint flag, jstring instance,
	jstring pathlib, jstring pathres, jdouble tStart, jdouble tEnd,
	jdouble time, jint toleranceDefined, jdouble tolerance, jint visible,
	jint loggingOn, jstring uid, jdoubleArray xdot_get, jdoubleArray x_get,
	jdoubleArray x_set, jdoubleArray invals, jlongArray invalrefs,
	jdoubleArray outvals, jlongArray outvalrefs, jlong x_dot_get_ref, 
	jdoubleArray xx_xdot_get, jlongArray xx_xdot_get_refs, 
	jdoubleArray uu_xdot_get, jlongArray uu_xdot_get_refs, jdoubleArray xdot_dot_get)

{
	if (flag == 0) {
		/////////////////////////////////////////
		// Measure time if comments disabled.
		// struct timespec start, end;
		// clock_gettime(CLOCK_MONOTONIC, &start);

		fmu_t* _c = (fmu_t*) calloc(1, sizeof(struct idfFmu_t));
		// FIXME: Do I need to free before reallocating?
		//if (insNum > 0){
		//	free (_c);
		//	fmu_t* _c = (idfFmu_t*) calloc(1, sizeof(struct fmu_t));
		//}
		// save the index
		_c->index = insNum;

		// Add the instance
		addfmuInstances(_c);

		if (xdot_get != NULL) {
			fmuInstances[_c->index]->nx = (int) (*env)->GetArrayLength(env,
				xdot_get);
		} else {
			return error("The number of state variables cannot be null");
		}

		fmuInstances[_c->index]->callbacks.logger = fmuLogger;
		fmuInstances[_c->index]->callbacks.allocateMemory = calloc;
		fmuInstances[_c->index]->callbacks.freeMemory = free;
		fmuInstances[_c->index]->callbacks.stepFinished = NULL; // not needed for co-execution
		fmuInstances[_c->index]->callbacks.componentEnvironment = _c; // pointer to current fmu from the environment.
		// get global parameters
		fmuInstances[_c->index]->startTime = tStart;
		fmuInstances[_c->index]->stopTime = tEnd;

		fmuInstances[_c->index]->visible = visible;
		fmuInstances[_c->index]->toleranceDefined = toleranceDefined;
		fmuInstances[_c->index]->tolerance = tolerance;
		fmuInstances[_c->index]->loggingOn = loggingOn;

		// previous time
		fmuInstances[_c->index]->prevTime = -1.0;

		// JNIEnv struct
		fmuInstances[_c->index]->env = env;

		// get the instance name
		fmuInstances[_c->index]->instanceName =
			(*fmuInstances[_c->index]->env)->GetStringUTFChars(
			fmuInstances[_c->index]->env, instance,
			NULL);

		// get the library path
		fmuInstances[_c->index]->fmuNativeLibraryLocation =
			(*fmuInstances[_c->index]->env)->GetStringUTFChars(
			fmuInstances[_c->index]->env, pathlib, NULL);

		// get the path to resource
		fmuInstances[_c->index]->fmuResourceLocation =
			(*fmuInstances[_c->index]->env)->GetStringUTFChars(
			fmuInstances[_c->index]->env, pathres, NULL);

		// get the guid
		fmuInstances[_c->index]->guid = (*env)->GetStringUTFChars(env, uid,
			NULL);

		// set the wrapup flag to true
		fmuInstances[_c->index]->wrapup = fmiTrue;

		// load shared library
		if (loadLib (_c->index)!=0) return error("unable to load shared libraries\n");

		// get fmiInstantiate
		fmuInstances[_c->index]->instantiate =
			(importFunctionInstantiate) getAdr(_c->index, "Instantiate");

		// get fmiSetupExperiment
		fmuInstances[_c->index]->setupExperiment =
			(importFunctionSetupExperiment) getAdr(_c->index,
			"SetupExperiment");

		// get fmiSetTime
		fmuInstances[_c->index]->setTime = (importFunctionSetTime) getAdr(
			_c->index, "SetTime");

		// get fmiEnterInitializationMode
		fmuInstances[_c->index]->enterInitialization =
			(importFunctionEnterInitializationMode) getAdr(_c->index,
			"EnterInitializationMode");

		// get fmiExitInitializationMode
		fmuInstances[_c->index]->exitInitialization =
			(importFunctionExitInitializationMode) getAdr(_c->index,
			"ExitInitializationMode");

		// get fmiSetTime
		fmuInstances[_c->index]->newDiscreteStates =
			(importFunctionNewDiscreteStates) getAdr(_c->index,
			"NewDiscreteStates");

		// get fmiEnterContinuousTimeMode
		fmuInstances[_c->index]->enterContinuousTimeMode =
			(importFunctionEnterContinuousTimeMode) getAdr(_c->index,
			"EnterContinuousTimeMode");

		// get fmiCompletedIntegratorStep
		fmuInstances[_c->index]->completedIntegratorStep =
			(importFunctionCompletedIntegratorStep) getAdr(_c->index,
			"CompletedIntegratorStep");

		// get fmiSetContinuousStates
		fmuInstances[_c->index]->setContinuousStates =
			(importSetContinuousStates) getAdr(_c->index,
			"SetContinuousStates");

		// get fmiGetDerivatives
		fmuInstances[_c->index]->getDerivatives = (importGetDerivatives) getAdr(
			_c->index, "GetDerivatives");

		// get fmiGetContinuousStates
		fmuInstances[_c->index]->getContinuousStates =
			(importGetContinuousStates) getAdr(_c->index,
			"GetContinuousStates");

		// get fmiGetReal
		fmuInstances[_c->index]->getReal = (importGetReal) getAdr(_c->index,
			"GetReal");

		// get fmiSetReal
		fmuInstances[_c->index]->setReal = (importSetReal) getAdr(_c->index,
			"SetReal");

		// FIXME: Terminate and free are causing code to crash?
		// get fmiTerminate
		fmuInstances[_c->index]->terminate = (importTerminate) getAdr(_c->index, "Terminate");

		// get fmiFreeInstance
		fmuInstances[_c->index]->freeInstance = (importFreeInstance) getAdr(_c->index, "FreeInstance");

		// get fmiGetDirectionalDerivative
		fmuInstances[_c->index]->getDirectionalDerivative = (importGetDirectionalDerivative) getAdr(_c->index, "GetDirectionalDerivative");

		// instantiate
		fmuInstances[_c->index]->c = fmuInstances[_c->index]->instantiate(
			fmuInstances[_c->index]->instanceName, fmiModelExchange,
			fmuInstances[_c->index]->guid,
			fmuInstances[_c->index]->fmuResourceLocation,
			&(fmuInstances[_c->index]->callbacks),
			fmuInstances[_c->index]->visible,
			fmuInstances[_c->index]->loggingOn);

		if (!fmuInstances[_c->index]->c) {
			// FIXME: when to release the path to the shared library
			(*fmuInstances[_c->index]->env)->ReleaseStringUTFChars(
				fmuInstances[_c->index]->env, pathlib,
				fmuInstances[idx]->fmuNativeLibraryLocation);
			(*fmuInstances[_c->index]->env)->ReleaseStringUTFChars(
				fmuInstances[_c->index]->env, pathres,
				fmuInstances[_c->index]->fmuResourceLocation);
			(*fmuInstances[_c->index]->env)->ReleaseStringUTFChars(
				fmuInstances[_c->index]->env, uid,
				fmuInstances[_c->index]->guid);
			return error("could not instantiate model");
		}

		// Release strings
		(*fmuInstances[_c->index]->env)->ReleaseStringUTFChars(
			fmuInstances[_c->index]->env, pathres,
			fmuInstances[_c->index]->fmuResourceLocation);
		(*fmuInstances[_c->index]->env)->ReleaseStringUTFChars(
			fmuInstances[_c->index]->env, uid,
			fmuInstances[_c->index]->guid);

		// allocate memory
		fmuInstances[_c->index]->x = (double *) calloc(
			fmuInstances[_c->index]->nx, sizeof(double));
		fmuInstances[_c->index]->xdot = (double *) calloc(
			fmuInstances[_c->index]->nx, sizeof(double));

		// setup the experiment, set the start time
		fmuInstances[_c->index]->fmiFlag =
			fmuInstances[_c->index]->setupExperiment(
			fmuInstances[_c->index]->c,
			fmuInstances[_c->index]->toleranceDefined,
			fmuInstances[_c->index]->tolerance,
			fmuInstances[_c->index]->startTime,
			fmiTrue, fmuInstances[_c->index]->stopTime);
		if (fmuInstances[_c->index]->fmiFlag > fmiWarning) {
			return error(
				"could not initialize model; failed FMI setup experiment");
		}
		// increase the counter
		insNum++;
		/*
		clock_gettime(CLOCK_MONOTONIC, &end);
		uint64_t timeElapsed = timespecDiff(&end, &start);
		printf("This is the time in ns spent to instantiate the FMU: %llu\n",
		timeElapsed);
		*/
		return _c->index;
	}
	// enter initialization
	else if (flag == 1) {
		/////////////////////////////////////////
		// Measure time if comments disabled.
		// struct timespec start, end;
		// clock_gettime(CLOCK_MONOTONIC, &start);
		// set time
		if (fmuInstances[idx]->prevTime != time) {
			fmuInstances[idx]->fmiFlag = fmuInstances[idx]->setTime(
				fmuInstances[idx]->c, time);
			fmuInstances[idx]->prevTime = time;
		}

		// initialize
		fmuInstances[idx]->fmiFlag = fmuInstances[idx]->enterInitialization(
			fmuInstances[idx]->c);
		if (fmuInstances[idx]->fmiFlag > fmiWarning) {
			return error(
				"could not initialize model; failed FMI enter initialization mode");
		}

		fmuInstances[idx]->fmiFlag = fmuInstances[idx]->exitInitialization(
			fmuInstances[idx]->c);
		if (fmuInstances[idx]->fmiFlag > fmiWarning) {
			return error(
				"could not initialize model; failed FMI exit initialization mode");
		}
		/*
		clock_gettime(CLOCK_MONOTONIC, &end);
		uint64_t timeElapsed = timespecDiff(&end, &start);
		printf(
		"This is the time in ns spent to terminate initialization of the FMU: %llu\n",
		timeElapsed);
		*/
		return 0;
	}
	// enter event mode.
	else if (flag == 2)
	{
		if (fmuInstances[idx]->prevTime != time) {
			fmuInstances[idx]->fmiFlag = fmuInstances[idx]->setTime(
				fmuInstances[idx]->c, time);
			fmuInstances[idx]->prevTime = time;
		}

		// no event iteration is assumed.
		fmuInstances[idx]->eventInfo.newDiscreteStatesNeeded = fmiFalse;
		fmuInstances[idx]->eventInfo.terminateSimulation = fmiFalse;

		// one call of fmiNewDiscreteStates is required according to the specification
		fmuInstances[idx]->fmiFlag = fmuInstances[idx]->newDiscreteStates(
			fmuInstances[idx]->c, &(fmuInstances[idx]->eventInfo));
		if (fmuInstances[idx]->fmiFlag > fmiWarning)
			return error("could not set a new discrete state");

		if (fmuInstances[idx]->eventInfo.newDiscreteStatesNeeded) {
			return error("need to do an event iteration at first invocation");
		}

		if (fmuInstances[idx]->eventInfo.terminateSimulation) {
			return error("model requested termination at first invocation");
		}

	}

	// enter continuous mode.
	else if (flag == 3)
	{
		if (fmuInstances[idx]->prevTime != time) {
			fmuInstances[idx]->fmiFlag = fmuInstances[idx]->setTime(
				fmuInstances[idx]->c, time);
			fmuInstances[idx]->prevTime = time;
		}

		// enter continuous mode
		fmuInstances[idx]->enterContinuousTimeMode(fmuInstances[idx]->c);
		return 0;
	}

	// get continuous states.
	else if (flag == 4)
	{
		if (fmuInstances[idx]->prevTime != time) {
			fmuInstances[idx]->fmiFlag = fmuInstances[idx]->setTime(
				fmuInstances[idx]->c, time);
			fmuInstances[idx]->prevTime = time;
		}

		// get continuous states.
		fmuInstances[idx]->fmiFlag = jgetContinuousStates(idx, x_get);
		return 0;
	}

	// get derivatives.
	else if (flag == 5)
	{
		if (fmuInstances[idx]->prevTime != time) {
			fmuInstances[idx]->fmiFlag = fmuInstances[idx]->setTime(
				fmuInstances[idx]->c, time);
			fmuInstances[idx]->prevTime = time;
		}

		// get the derivatives
		fmuInstances[idx]->fmiFlag = jgetDerivatives(idx, xdot_get);
		return 0;
	}

	// set continuous states.
	else if (flag == 6)
	{
		if (fmuInstances[idx]->prevTime != time) {
			fmuInstances[idx]->fmiFlag = fmuInstances[idx]->setTime(
				fmuInstances[idx]->c, time);
			fmuInstances[idx]->prevTime = time;
		}

		// set the continuous states.
		fmuInstances[idx]->fmiFlag = jsetContinuousStates(idx, x_set);
		return 0;
	}

	// set single inputs
	else if (flag == 7) {
		// Code below can be used to measure execution time.
		// struct timespec start, end;
		// clock_gettime(CLOCK_MONOTONIC, &start);
		// ncallsSetInputs++;
		// set the time
		if (time <= fmuInstances[idx]->stopTime) {
			if (fmuInstances[idx]->prevTime != time) {
				fmuInstances[idx]->fmiFlag = fmuInstances[idx]->setTime(
					fmuInstances[idx]->c, time);
				fmuInstances[idx]->prevTime = time;
			}
			if (fmuInstances[idx]->fmiFlag > fmiWarning) {
				return error("could not set time");
			}

			// set the inputs if any
			if (invals != NULL && invalrefs != NULL) {
				fmuInstances[idx]->fmiFlag = jsetReal(idx, invals,
					invalrefs);
			}
			/*
			// Code below can be used to measure execution time.
			clock_gettime(CLOCK_MONOTONIC, &end);
			uint64_t timeElapsed = timespecDiff(&end, &start);
			sumTimeElapsedSetInputs=sumTimeElapsedSetInputs + timeElapsed;
			*/
		} 
		return 0;
	}
	// get single outputs
	else if (flag == 8) {
		// Code below can be used to measure execution time.
		// struct timespec start, end;
		// clock_gettime(CLOCK_MONOTONIC, &start);
		// ncallsGetOutputs++;
		if (time <= fmuInstances[idx]->stopTime) {
			// set the time
			if (fmuInstances[idx]->prevTime != time) {

				fmuInstances[idx]->fmiFlag = fmuInstances[idx]->setTime(
					fmuInstances[idx]->c, time);
				fmuInstances[idx]->prevTime = time;
			}
			if (fmuInstances[idx]->fmiFlag > fmiWarning) {
				return error("could not set time");
			}

			// get the outputs if any
			if (outvals != NULL && outvalrefs != NULL) {
				fmuInstances[idx]->fmiFlag = jgetReal(idx, outvals,
					outvalrefs);
			}
			/*
			// Code below can be used to measure execution time.
			clock_gettime(CLOCK_MONOTONIC, &end);
			uint64_t timeElapsed = timespecDiff(&end, &start);
			sumTimeElapsedGetOutputs=sumTimeElapsedGetOutputs + timeElapsed;
			*/
		} 
	}
	// set complete integrator
	else if (flag == 9) {

		// Code below can be used to measure execution time.
		// struct timespec start, end;
		// clock_gettime(CLOCK_MONOTONIC, &start);
		// ncallsCompDerivs++;

		// check for step event, e.g. dynamic state selection
		fmuInstances[idx]->fmiFlag =
			fmuInstances[idx]->completedIntegratorStep(
			fmuInstances[idx]->c, fmiTrue,
			&(fmuInstances[idx]->stepEvent),
			&(fmuInstances[idx]->terminateSimulation));

		/*
		// Code below can be used to measure execution time.
		clock_gettime(CLOCK_MONOTONIC, &end);
		uint64_t timeElapsed = timespecDiff(&end, &start);
		sumTimeComputeDerivatives=sumTimeComputeDerivatives + timeElapsed;
		*/

		if (fmuInstances[idx]->fmiFlag > fmiWarning)
			return error("could not complete integrator step");

		if (fmuInstances[idx]->terminateSimulation) {
			wrapup(idx);
		}

		return 0;
	}

	// call end of the simulation
	else if (flag == -1) {
		/*
		printf ("The end of the simulation flag was received for FMU instance ---%s.\n",
		fmuInstances[idx]->instanceName);
		*/
		// Release strings
		(*fmuInstances[idx]->env)->ReleaseStringUTFChars(
			fmuInstances[idx]->env, pathlib,
			fmuInstances[idx]->fmuNativeLibraryLocation);
		(*fmuInstances[idx]->env)->ReleaseStringUTFChars(
			fmuInstances[idx]->env, instance,
			fmuInstances[idx]->instanceName);
		wrapup(idx);
		return 0;
	}
	// get directional derivatives. for Qss2 where time is not set, and states are not set too.
	else if (flag == -10) {
		// Code below can be used to measure execution time.
		// struct timespec start, end;
		// clock_gettime(CLOCK_MONOTONIC, &start);
		// ncallsGetOutputs++;
		// get the outputs if any
		if (xx_xdot_get != NULL || uu_xdot_get != NULL) {
			fmuInstances[idx]->fmiFlag = jgetDirectionalDerivative(idx, x_dot_get_ref, xx_xdot_get,
				xx_xdot_get_refs, uu_xdot_get, uu_xdot_get_refs, xdot_dot_get);
		}
		/*
		// Code below can be used to measure execution time.
		clock_gettime(CLOCK_MONOTONIC, &end);
		uint64_t timeElapsed = timespecDiff(&end, &start);
		sumTimeElapsedGetOutputs=sumTimeElapsedGetOutputs + timeElapsed;
		*/
		return 0;
	}
	return 0;
}

