/*  Invoke a Functional Mock-up Interface (FMI) 2.0 Model Exchange Functional
    Mock-up Unit (FMU) which will be integrated using QSS.

   Copyright (c) 2014-2015 The Regents of the University of California.
   All rights reserved.
   Permission is hereby granted, without written agreement and without
   license or royalty fees, to use, copy, modify, and distribute this
   software and its documentation for any purpose, provided that the above
   copyright notice and the following two paragraphs appear in all copies
   of this software.

   IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
   FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
   ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
   THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
   SUCH DAMAGE.

   THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
   INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
   MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
   PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
   CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
   ENHANCEMENTS, OR MODIFICATIONS.

   PT_COPYRIGHT_VERSION_2
   COPYRIGHTENDKEY

 */
package ptolemy.actor.lib.fmi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.ptolemy.fmi.FMI20ContinuousStateDerivative;
import org.ptolemy.fmi.FMI20EventInfo;
import org.ptolemy.fmi.FMILibrary;
import org.ptolemy.fmi.FMIModelDescription;
import org.ptolemy.fmi.FMUFile;
import org.ptolemy.fmi.FMIModelDescription.ContinuousState;
import org.ptolemy.fmi.FMIScalarVariable;
import org.ptolemy.fmi.FMIScalarVariable.Causality;
import org.ptolemy.fmi.NativeSizeT;
import org.ptolemy.fmi.type.FMIBooleanType;
import org.ptolemy.fmi.type.FMIIntegerType;
import org.ptolemy.fmi.type.FMIRealType;
import org.ptolemy.fmi.type.FMIStringType;
import org.ptolemy.qss.solver.QSSBase;
import org.ptolemy.qss.util.DerivativeFunction;
import org.ptolemy.qss.util.ModelPolynomial;

import ptolemy.actor.Director;
import ptolemy.actor.IOPort;
import ptolemy.actor.Initializable;
import ptolemy.actor.NoRoomException;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.lib.fmi.FMUImport;
import ptolemy.actor.util.Time;
import ptolemy.data.BooleanToken;
import ptolemy.data.DoubleToken;
import ptolemy.data.SmoothToken;
import ptolemy.data.Token;
import ptolemy.data.expr.FileParameter;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.domains.qss.kernel.QSSDirector;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.Settable;
import ptolemy.util.StringUtilities;

/**
 * Invoke a Functional Mock-up Interface (FMI) 2.0 Model Exchange Functional
 * Mock-up Unit (FMU) which will be integrated using QSS.
 *
 * <p>
 * Read in a <code>.fmu</code> file named by the <i>fmuFile</i> parameter. The
 * <code>.fmu</code> file is a zipped file that contains a file named
 * <code>modelDescription.xml</code> that describes the ports and parameters
 * that are created. At run time, method calls are made to C functions that are
 * included in shared libraries included in the <code>.fmu</code> file.
 * </p>
 *
 * <p>
 * To use this actor from within Vergil, use File -&gt; Import -&gt; Import FMU
 * for QSS integration, which will prompt for a .fmu file. This actor is
 * <b>not</b> available from the actor pane via drag and drop. The problem is
 * that dragging and dropping this actor ends up trying to read fmuImport.fmu,
 * which does not exist. If we added such a file, then dragging and dropping the
 * actor would create an arbitrary actor with arbitrary ports.
 * </p>
 * This actor uses any QSS solver to integrate the FMUs for model exchange. A
 * package of QSS solvers can be found in org.ptolemy.qss. This actor extends
 * from FMUImport.java and should be used along with the QSSDirector.
 *
 * @author Thierry S. Nouidui, David M. Lorenzetti, Michael Wetter, Christopher Brooks.
 * @version $Id: FMUQSS.java 71705 2015-03-10 00:00:35Z cxh $
 * @since Ptolemy II 11.0
 * @Pt.ProposedRating Red (cxh)
 * @Pt.AcceptedRating Red (cxh)
 */
public class FMUQSS extends FMUImport implements DerivativeFunction {

    /**
     * Construct an actor with the given container and name.
     *
     * @param container The container.
     * @param name The name of this actor.
     * @exception IllegalActionException If the actor cannot be
     * contained by the proposed container.
     * @exception NameDuplicationException If the container already
     * has an actor with this name.
     */
    public FMUQSS(CompositeEntity container, String name)
            throws NameDuplicationException, IllegalActionException {
        super(container, name);
        initFMUParameters = new Parameter(this, "initFMUParameters");
        initFMUParameters.setTypeEquals(BaseType.BOOLEAN);
        // initFMUParameters initialize parameters which have the FMIRealType.
        initFMUParameters.setExpression("false");

        // The modelExchange parameter in the parent class is marked
        // as expert, which means it is not usually visible to the
        // user. QSS FMUs are always model exchange, so we change the
        // visibility to none just to be sure no one tries to edit it.
        // We can use the parent attributeChanged method.
        modelExchange.setExpression("true");
        modelExchange.setVisibility(Settable.NONE);

        // The persistenInputs parameter in the parent class is marked
        // as false. QSS FMUs do not support "absent" values, so we change the
        // parameter to true so a user does not need to set this manually.
        persistentInputs.setExpression("true");

        // stateVariablesAsInputPorts is true in the parent class and
        // it is used by FMUImport._getInputs().
        stateVariablesAsInputPorts.setExpression("false");

        _attachText("_iconDescription", "<svg>\n"
                + "<rect x=\"-30\" y=\"-20\" " + "width=\"60\" height=\"40\" "
                + "style=\"fill:white\"/>\n" + "<text x=\"-25\" y=\"7\" "
                + "style=\"font-size:12\">\n" + "FMUQSS" + "</text>\n"
                + "</svg>\n");

    }

    ///////////////////////////////////////////////////////////////////
    ////                     ports and parameters                  ////

    /**
     * If true, indicate the FMU to initialize parameters variables
     * parameters.  The default value is true.
     */
    public Parameter initFMUParameters;

    ///////////////////////////////////////////////////////////////////
    ////                         public methods                    ////
    
    /**
     * Evaluate the derivative function.
     *
     * @param time The simulation time.
     * @param stateVariables The vector of state variables at <code>time</code>.
     * @param inputVariables The vector of input variables at <code>time</code>.
     * @param stateVariableDerivatives The (output) vector of time rates of change of the state variables 
     * at <code>time</code>.
     * @return Success (0 for success, else user-defined error code).
     * @exception IllegalActionException
     */
    public final int evaluateDerivatives(final Time time, final double[] stateVariables,
            final double[] inputVariables, final double[] stateVariableDerivatives)
            throws IllegalActionException {

        // Check assumptions.
        assert (getStateCount() > 0);
        assert (stateVariables.length == getStateCount());
        assert (stateVariableDerivatives.length == getStateCount());

        // Give {time} to the FMU.
        final double timeValue = time.getDoubleValue();
        
        // Set the continuous states.
        runNativeFMU(_fmiComponent, 6, null, null, null, 0.0, 0.0, timeValue,
                0, 0.0, 0, 0, null, null, null, stateVariables, null, null, 
                null, null, 0, null, null, null, null, null);

        // Handle any time, state or step event.
        // FIXME: We do not handle events in this code.
        //_handleEvents(timeValue);

        // Give {inputVariable} to the FMU.
        for (int ii = 0; ii < _inputs.size(); ++ii) {
            final FMIScalarVariable scalar = _inputs.get(ii).scalarVariable;
            assert (scalar.type instanceof FMIRealType);
            final Input input = _inputs.get(ii);
            if (!input.hasChanged && !_firstRound) {
                continue;
            }
            final double tmp_uu[] = { inputVariables[ii] };
            final long tmp_uuRef[] = { scalar.valueReference };
            // Set the inputs which have changed.
            runNativeFMU(_fmiComponent, 7, null, null, null, 0.0, 0.0,
                    timeValue, 0, 0.0, 0, 0, null, null, null, null, tmp_uu,
                    tmp_uuRef, null, null, 0, null, null, null, null, null);
            _inputs.get(ii).hasChanged = false;
        }
        
        // Retrieve {xdot}.
        runNativeFMU(_fmiComponent, 5, null, null, null, 0.0, 0.0, timeValue,
                0, 0.0, 0, 0, null, stateVariableDerivatives, null, null, null, 
                null, null, null, 0, null, null, null, null, null);

        return 0;
    }

    /**
     * Evaluate directional derivative.
     *
     * @param stateDerivIndex The state derivative index.
     * @param stateVariableDerivatives The state derivatives.
     * @param inputVariableDerivatives The input derivatives.
     * @return The directional derivative (see fmi2GetDirectionalDerivatives in FMI specification).
     * @exception IllegalActionException If thrown while computing the directional derivative.
     */
    public final double evaluateDirectionalDerivatives(final int stateDerivIndex,
            final double[] stateVariableDerivatives, final double[] inputVariableDerivatives)
            throws IllegalActionException {
        // Get the current continuous state
        final FMI20ContinuousStateDerivative stateDerivative = _fmiModelDescription.continuousStateDerivatives
                .get(stateDerivIndex);
        final long stateDerivRef = stateDerivative.scalarVariable.valueReference;
        double[] stateSecondDerivative = new double[1];

        // Get the dependent inputs and match the indexes to the input list.
        final int numDepInputs = stateDerivative.dependentInputIndexes.size();
        final double[] tmpInputs = new double[numDepInputs];
        final long[] tmpInputsRefs = new long[numDepInputs];

        for (int ii = 0; ii < numDepInputs; ++ii) {
            final int curIdx = stateDerivative.dependentInputIndexes.get(ii);
            final Input input = _inputs.get(curIdx);
            tmpInputs[ii] = inputVariableDerivatives[curIdx];
            tmpInputsRefs[ii] = input.scalarVariable.valueReference;
        }

        // Get the dependent states and match the indexes to the continuous state list.
        final int numDepStates = stateDerivative.dependentStateIndexes.size();
        final double[] tmpStates = new double[numDepStates];
        final long[] tmpStatesRefs = new long[numDepStates];

        for (int ii = 0; ii < numDepStates; ++ii) {
            final int curIdx = stateDerivative.dependentStateIndexes.get(ii);
            final ContinuousState state = _fmiModelDescription.continuousStates
                    .get(curIdx);
            tmpStates[ii] = stateVariableDerivatives[curIdx];
            tmpStatesRefs[ii] = state.scalarVariable.valueReference;
        }

        // Give {stateVariableDerivatives} and {inputVariableDerivatives} and retrieve {xdot_dot}.
        // FIXME: This can only be used for Qss2. The assumption being here that
        // the time and the continuous have been set previously.
        runNativeFMU(_fmiComponent, -10, null, null, null, 0.0, 0.0, 0.0, 0, 0.0,
                0, 0, null, null, null, null, null, null, null, null,
                stateDerivRef, tmpStates, tmpStatesRefs, tmpInputs,
                tmpInputsRefs, stateSecondDerivative);
        
        return stateSecondDerivative[0];
    }
    
    
    /**
     * Override the FMUImport base class to produce outputs.
     *
     * <p> According to "System Design, Modeling, and Simulation Using Ptolemy II",
     * version 1.02, section 12.3.1 "Execution Control": "The main computation
     * of the actor is typically performed during the fire action, when it reads
     * input data, performs computation, and produces output data."</p>
     *
     * If it is time to produce a quantized output, produce it. If necessary to
     * catch up to current time, and then set the (known) inputs of the FMU and
     * retrieve and send out any outputs for which all inputs on which the
     * output depends are known.
     * 
     * @exception IllegalActionException If FMU indicates a failure.
     */
    
    public void fire() throws IllegalActionException {
        // By design, this method does not call super.fire(), because
        // this class uses a different algorithm that is specific
        // to QSS.  However, we need to do what AtomicActor.fire()
        // does:
        if (_debugging) {
            _debug("Called fire()");
        }
        // end of AtomicActor.java
        
        // Get current simulation time.
        final Time currentTime = _director.getModelTime();
        if (_debugging) {
            int currentMicrostep =  _director.getIndex();
            _debugToStdOut(String.format(
                    "FMUQSS.fire() on id{%d} at time %s, microstep %d",
                    System.identityHashCode(this), currentTime.toString(),
                    currentMicrostep));
        }
        
        // Initialize the input variable models.
        if (_firstRound) {
            _initializeQSSIntegratorInputVariables(currentTime);
            _firstRound = false;
        }
        
        // Assume do not need a quantization-event.
        assert (_qssSolver.needQuantizationEventIndex() == -1);

        // Step.
        // Only step if it will advance the integrator.
        // TODO: Consider instead relaxing the integrator to allow steps that
        // do nothing.
        // TODO: Still need to figure out whether/how to commit integrator to a
        // step, in cases where Ptolemy calls the fire() and postfire() methods
        // multiple times at a single step.
        if (_qssSolver.getCurrentSimulationTime().compareTo(currentTime) < 0) {
            try {
                _qssSolver.stepToTime(currentTime);
            } catch (Exception ee) {
                throw new IllegalActionException(this, ee.getMessage());
            }
            // Requantize if necessary.
            _triggerQuantizationEvents(currentTime, false);
        }
        
        _numberOfSteps++;
    }

    /**
     * Return the count of input variables.
     *
     * @return The number of input variables.
     */
    public final int getInputVariableCount() {
        return _inputs.size();
    }

    /**
     * Return the count of state variables.
     * 
     * @return The number of state variables.
     */
    public final int getStateCount() {
        return _fmiModelDescription.numberOfContinuousStates;
    }

    /**
     * Indicate existence of directional derivatives.
     *
     * @return True if directional derivatives are provided.
     */
    public final boolean getProvidesDirectionalDerivatives() {
        return _fmiModelDescription.providesDirectionalDerivative;
    }

    /**
     * Override the importFMU in FMUImport base class.
     * 
     * @param originator The originator of the change request.
     * @param fmuFileParameter The .fmuFile
     * @param context The context in which the FMU actor is created.
     * @param x The x-axis value of the actor to be created.
     * @param y The y-axis value of the actor to be created.
     * @exception IllegalActionException If there is a problem instantiating the actor.
     * @exception IOException If there is a problem parsing the fmu file.
     */
    public static void importFMU(Object originator,
            FileParameter fmuFileParameter, NamedObj context, double x, double y)
            throws IllegalActionException, IOException {
        // FIXME: we should use a factory here.  The issue is that
        // when we import a fmu for QSS, we want to call
        // FMUQSS._acceptFMU().  However, we are using static methods
        // because we use a MoMLChangeRequest to instantiate the actor
        // so we can't use object-oriented design to have the subclass
        // provide an implementation of _acceptFMU(). ick.
        Method acceptFMUMethod = null; 
        try {
            Class clazz = Class.forName("ptolemy.actor.lib.fmi.FMUQSS");
            acceptFMUMethod = clazz.getDeclaredMethod("_acceptFMU", new Class []  {FMIModelDescription.class});
        } catch (Throwable throwable) {
            throw new IllegalActionException(context, throwable,
                    "Failed to get the static _acceptFMU(FMIModelDescription) method for FMUImport.");
        }

        // Note that this method is declared to not have a modelExchange
        // parameter because all QSS fmus are model exchange fmus.  However,
        // _importFMU does take a modelExchange parameter.
        FMUImport._importFMU(originator, fmuFileParameter, context, x, y,
                true /* modelExchange */,
                false /*addMaximumStepSize*/,
                false /* stateVariablesAsInputPorts */,
                "ptolemy.actor.lib.fmi.FMUQSS",
                acceptFMUMethod);
    }

    /**
     * Initialize this FMU wrapper.
     *
     * <p>According to "System Design, Modeling, and Simulation Using Ptolemy II",
     * version 1.02, section 12.3.1 "Execution Control": "The initialize action
     * of the setup phase initializes parameters, resets local state, and sends
     * out any initial messages."</p>
     *
     * @exception IllegalActionException If the slave FMU cannot be initialized.
     */
    public void initialize() throws IllegalActionException {
        // By design, this method does not call super.initialize(),
        // because this class uses a different algorithm that is
        // specific to QSS.  However, we need to do what
        // AtomicActor.initialize() does:

        if (_debugging) {
            _debug("Called initialize()");
        }
        // First invoke initializable methods.
        if (_initializables != null) {
            for (Initializable initializable : _initializables) {
                initializable.initialize();
            }
        }
        // end of AtomicActor.java

        if (_debugging) {
            _debugToStdOut(String.format("FMUQSS.initialize() on id{%d}",
                    System.identityHashCode(this)));
        }

        // Set a flag so the first call to fire() can do appropriate
        // initialization.
        _firstRound = true;
       
        // Get the model identifier from the model description.
        final String modelIdentifier = _fmiModelDescription.modelIdentifier;

        // There is no simulator UI.
        byte toBeVisible = 0;
        if (((BooleanToken) visible.getToken()).booleanValue()) {
            toBeVisible = 1;
        }

        // FIXME: We should send logging messages to the debug listener.
        byte loggingOn = _debugging ? (byte) 1 : (byte) 0;

        // Get the path to the native FMU library.
        String fmuLibPath = null;
        try {
            fmuLibPath = _fmiModelDescription.getNativeLibraryPath();
        } catch (IOException ex) {
            throw new IllegalActionException(this, ex,
                    "Could not find path to the native library.");
        }

        // Instantiate FMU
        final Time currentTime = _director.getModelTime();
        final Time startTime = _director.getModelStartTime();
        final Time stopTime = _director.getModelStopTime();
        _quantumScaleFactor =  _director.getQuantumScaleFactor();
        _internalRelativeQuantum = _director.getRelativeQuantum() * _quantumScaleFactor;
        
        // Initialize number of continuous states.
        double[] derivatives = new double[_fmiModelDescription.numberOfContinuousStates];
        
        _fmiComponent = runNativeFMU(0,  0, modelIdentifier, fmuLibPath,
                _fmiModelDescription.fmuResourceLocation,
                startTime.getDoubleValue(),stopTime.getDoubleValue(),
                currentTime.getDoubleValue(), 0, _internalRelativeQuantum, toBeVisible,
                loggingOn, _fmiModelDescription.guid, derivatives, null, null,
                null, null, null, null, 0, null, null, null, null, null);

        // Initialize FMU parameters
        if (((BooleanToken) initFMUParameters.getToken()).booleanValue()) {
            // Do not initialize parameters which might not be real.
            // Need to throw and exception here to notify the user that it won' work.
            _initializeFMUParameters();
        }

        // Initialize FMU
        runNativeFMU(_fmiComponent, 1, null, null, null, 0.0, 0.0,
                currentTime.getDoubleValue(), 0, 0.0, 0, 0, null,
                null, null, null, null, null, null, null, 0, null,
                null, null, null, null);
        
        // Enter discrete states in FMU
        runNativeFMU(_fmiComponent, 2, null, null, null, 0.0, 0.0,
                currentTime.getDoubleValue(), 0, 0.0, 0, 0, null,
                null, null, null, null, null, null, null, 0, null,
                null, null, null, null);
        
        // Enter continuous states in FMU
        runNativeFMU(_fmiComponent, 3, null, null, null, 0.0, 0.0,
                currentTime.getDoubleValue(), 0, 0.0, 0, 0, null,
                null, null, null, null, null, null, null, 0, null,
                null, null, null, null);  

        // Get and configure the QSS integrator.
        _createQSSSolver();
        
        // To make sure this actor fires at the start time, request a firing.
        getDirector().fireAtCurrentTime(this);

        return;

    }

    /**
     * Return false if any output has been found that not depend
     * directly on an input.
     * 
     * @return False if this actor can be fired without all inputs
     * being known.
     */
    @Override
    public boolean isStrict() {
        return _isStrict;
    }
    
    /**
     * Interface to the FMU.
     *
     * <p>
     * This methods calls the FMU using JNI with different flags.
     * 0: Instantiate.
     * 1: Initialize. 
     * 2: Enter event mode. 
     * 3: Enter continuous mode.
     * 4: Get continuous states.
     * 5: Get state derivatives.
     * 6: Set continuous states.
     * 7: Set single inputs.
     * 8: Get single outputs.
     * 9: Complete integrator.
     * -1: Terminate simulation.
     * -10: Get directional derivatives."
     * </p>
     * @param idx The index of the FMU instance.
     * @param fla The flag to determine the FMI functions to call.
     * @param instance The FMU instance name. 
     * @param jniResourceLocation The path to the FMU native library.
     * @param fmuResourceLocation The path to the FMU resource location.
     * @param tStart The FMU simulation start time.
     * @param tEnd the FMU simulation end time.
     * @param time The current simulation time.
     * @param toleranceDefined the flag is true if sover tolerance is defined.
     * @param tolerance The FMU solver tolerance.
     * @param visible The FMU visible flag.
     * @param loggingOn The FMU logginOn flag.
     * @param guid The FMU GUID.
     * @param continuousStateDerivatives The FMU state derivatives to get.
     * @param continuousStatesFromFMU The FMU continuous states to get.
     * @param continuousStatesToFMU The FMU continuous states to set.
     * @param inputValues The FMU input values.
     * @param inputValueReferences The FMU input value references.
     * @param outputValues The FMU output values.
     * @param outputValueReferences The FMU output value references.
     * @param continuousStateDerivativesValueReference The FMU state derivative value reference.
     * @param directionalStateDerivatives The FMU directional state derivatives.
     * @param directionalStateDerivativesValueReferences The FMU directional state derivatives value references.
     * @param directionalInputDerivatives The FMU directional input derivatives.
     * @param directionalInputDerivativesValueReferences The FMU directional input derivatives value references.
     * @param continuousSecondDerivatives The FMU second derivatives.
     * @return FMU instance The index when fla is zero.
     */
    public static native int runNativeFMU(int idx, int fla, String instance,
            String jniResourceLocation, String fmuResourceLocation, double tStart, 
            double tEnd, double time, int toleranceDefined, double tolerance, int visible,
            int loggingOn, String guid, double[] continuousStateDerivatives, 
            double[] continuousStatesFromFMU, double[] continuousStatesToFMU, 
            double[] inputValues, long[] inputValueReferences, double[] outputValues, 
            long[] outputValueReferences, long continuousStateDerivativesValueReference, 
            double[] directionalStateDerivatives, long[] directionalStateDerivativesValueReferences, 
            double[] directionalInputDerivatives, long[] directionalInputDerivativesValueReferences, 
            double[] continuousSecondDerivatives);

    /**
     * Update the calculation of the next output time and request a refiring at
     * that time.
     *
     * <p>According to "System Design, Modeling, and Simulation Using Ptolemy II",
     * version 1.02, section 12.3.1 "Execution Control": "An actor may have
     * persistent state that evolves during execution; the postfire action
     * updates that state in response to any inputs. The fact that the state of
     * an actor is updated only in postfire is an important part of the actor
     * abstract semantics..."</p>
     *
     * If there is a new input, read it and update the slope.
     *
     * @return True if the base class returns true.
     * @exception IllegalActionException If reading inputs or parameters fails.
     */
    public boolean postfire() throws IllegalActionException {
        // By design, this method does not call super.fire(), because
        // this class uses a different algorithm that is specific
        // to QSS.  However, we need to do what AtomicActor.postfire()
        // does:
        if (_debugging) {
            _debug("Called postfire()");
        }

        // Get current simulation time.
        final Time currentTime = _director.getModelTime();
        if (_debugging) {
            int currentMicrostep = _director.getIndex();
            _debugToStdOut(String.format(
                    "FMUQSS.postfire() on id{%d} at time %s, microstep %d",
                    System.identityHashCode(this), currentTime.toString(),
                    currentMicrostep));
        }

        // Update the internal, continuous state models if necessary.
        _triggerRateEvent(currentTime, false);

        // Find the next firing time, assuming nothing else in simulation
        // changes.
        final Time possibleFireAtTime = _qssSolver
                .predictQuantizationEventTimeEarliest();
        if (_debugging) {
            _debugToStdOut(String.format("-- Id{%d} want to fire by time %s",
                    System.identityHashCode(this),
                    possibleFireAtTime.toString()));
        }

        // Cancel firing time if necessary.
        final boolean possibleDiffersFromLast = (null == _lastFireAtTime || possibleFireAtTime
                .compareTo(_lastFireAtTime) != 0);
        if (null != _lastFireAtTime // Made request before.
                && _lastFireAtTime.compareTo(currentTime) > 0 // Last request was
                                                            // not used.
                                                            // _lastFireAtTime >
                                                            // currentTime
                && possibleDiffersFromLast // Last request is no longer valid.
        ) {
            if (_debugging) {
                _debugToStdOut(String.format(
                        "-- Id{%d} cancel last fire request (time %s)",
                        System.identityHashCode(this),
                        _lastFireAtTime.toString()));
            }
            _director.cancelFireAt(this, _lastFireAtTime);
        }

        // Request firing time if necessary.
        if (possibleDiffersFromLast && !possibleFireAtTime.isPositiveInfinite()) {
            if (_debugging) {
                _debugToStdOut(String.format(
                        "-- Id{%d} request fire by time %s",
                        System.identityHashCode(this),
                        possibleFireAtTime.toString()));
            }
            getDirector().fireAt(this, possibleFireAtTime);
            _lastFireAtTime = possibleFireAtTime;
        }

        // As we are not calling super.postfire(), we need
        // to do what AtomicActor.postfire() does and check
        // to see if stop was requested:
        return !_stopRequested;
    }

    /**
     * Redeclare input/output dependencies.
     * 
     * @exception IllegalActionException if an exception occurs.
     */
    public void preinitialize() throws IllegalActionException {
        // By design, this method does not call super.preinitialize(), because
        // this class uses a different algorithm that is specific
        // to QSS.  However, we need to do what AtomicActor.preinitialize()
        // does:
        if (_debugging) {
            _debug("Called preinitialize()");
        }
        
        _stopRequested = false;

        // For backward compatibility, in case there are actors
        // that override pruneDependencies() to alter their
        // causality interface, call it here.
        pruneDependencies();

        // Declare dependency for this actor. For actors such as
        // TimeDelay, the delay dependency between input and output
        // ports are declared.
        declareDelayDependency();

        // First invoke initializable methods.
        if (_initializables != null) {
            for (Initializable initializable : _initializables) {
                initializable.preinitialize();
            }
        }
        
        // end of AtomicActor.java
        
        // Initialize counter for number of steps.
        _numberOfSteps = 0;
        
        // Indicate that only parameters with FMIRealType will be updated.
        if (((BooleanToken) initFMUParameters.getToken()).booleanValue()) {
            new Exception("Warning: initFMUParameters has been set to true for actor " + this.getDisplayName()
                    + " but only parameters from type FMIRealType will be updated.").printStackTrace();
        }        
        // load the JNI wrapper
        // FIXME: Need to decide where this library should reside?
        // The library currently resides in ptolemy/actor/lib/fmi/
        try {
            final String jniLibPath = _jniPathSharedLibrary();
            System.load(jniLibPath);
        } catch (UnsatisfiedLinkError linkerException) {
            throw new IllegalActionException(this,
                    "Native code library with path " + _jniLibPath
                    + " failed to load.\n" + linkerException);
        }
        catch (IOException linkerException) {
            throw new IllegalActionException(this, 
                    "Native code library with path " + _jniLibPath
                    + " failed to load.\n" + linkerException);
        }
        
        // Initialize the input list.
        _inputs = new LinkedList<Input>();
        
        // Initialize the output list.
        _outputs = new LinkedList<Output>();
        
        // Get the inputs from super class.
        _inputs = _getInputs();
        
        // Get the outputs from super class
        _outputs = _getOutputs();
        
        // Initialize the continuous states.
        _initializeContinuousStates();
        
        // Get the indexes of dependent variables
        _getStateDerivativesDependenciesIndexes();
                
        // Get director and check its type.
        Director director = getDirector();
        if (!(director instanceof QSSDirector)) {
            throw new IllegalActionException(
                    this,
                    String.format(
                            "Director %s cannot be used for QSS, which requires a QSSDirector.",
                            director.getName()));
        }
        _director = (QSSDirector) director;
        
    }

    /**
     * Terminate and free the slave fmu.
     * 
     * @exception IllegalActionException If the slave fmu cannot be
     * terminated or freed.
     */
    public void wrapup() throws IllegalActionException {
        // By design, this method does not call super.fire(), because
        // this class uses a different algorithm that is specific
        // to QSS.  However, we need to do what AtomicActor.fire()
        // does:
        if (_debugging) {
            _debug("Called wrapup()");
        }
        // Invoke initializable methods.
        if (_initializables != null) {
            for (Initializable initializable : _initializables) {
                initializable.wrapup();
            }
        }
        
        // end of AtomicActor.java
        
        // Allow eventInfo to be garbaged collected.
        _eventInfo = null;        
        System.out.println ("Actor: "  + this.getFullName() + " has been called "
                + "to produce outputs: " + _numberOfSteps + " times.");       
        runNativeFMU(_fmiComponent, -1, null, null, null, 0.0, 0.0, 0.0, 0, 0.0, 0, 0, null,
                null, null, null, null, null, null, null, 0, null, null, null, null, null);
        
        // Reset reporting time to null.
        _reportingTime = null;
   
    }

    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////
    
    /** Determine if the model description is acceptable.  
     *  FMUQSS only works with FMI-2.0 (and later) model exchange fmus.   
     *  @param fmiModelDescription The description of the model to be checked.
     *  @return true if the model description is acceptable.
     *  @exception IOException If the model description is not acceptable
     */
    protected static boolean _acceptFMU(FMIModelDescription fmiModelDescription)
            throws IOException {
        // Check if the version of the FMU is higher than 2.0
        if (fmiModelDescription.fmiVersion.compareTo("2.0") < 0) {
            throw new IOException("The FMI version of this FMU is: "
                    + fmiModelDescription.fmiVersion
                    + "which is not supported.  "
                    + "QSS currently only supports FMI version 2.0.");
        }

        // Check if the FMU is for model exchange.
        if (fmiModelDescription.modelExchangeCapabilities == null) {
            throw new IOException("There is no ModelExchange attribute in the model description"
                    + " file of This FMU to indicate whether it is for model exchange or not.  "
                    + "QSS currently only supports FMU for model exchange.");
        }

        // Check to see if this FMU is for model exchange.
        // FMUFile checks modelDescription.xml for a ModelExchange element
        // and sets FMIModelDescription.modelExchange accordingly
        if (!fmiModelDescription.modelExchange) {
            throw new IOException("The FMU is not a model exchange FMU.  "
                    + "Perhaps the modelDescription.xml file does not have a "
                    + "\"ModelExchange\" element?  "
                    + "QSS requires a model exchange FMU.");
        }

        // Check if the FMU has at least one state variable.
        //if (fmiModelDescription.continuousStates.size() < 1) {
        //    throw new IOException("The number of continuous states of this FMU is: "
        //            + fmiModelDescription.numberOfContinuousStates
        //            + ".  The FMU does not have any state variables.  "
        //            + "The FMU needs to have at least one state variable. Please check the FMU.");
        //}

        return true;
    }
    
    /** Return true if the two arguments are within epsilon of one another.
     *  @param arg1 First argument.
     *  @param arg2 Second argument.
     *  @param epsilon The maximum difference.
     */
    protected final boolean _isClose(double arg1, double arg2, double epsilon) {
        if (arg1 > arg2 + epsilon || arg1 < arg2 - epsilon) {
            return false;
        } else {
            return true;
        }
    }

    ///////////////////////////////////////////////////////////////////
    ////                         private methods                   ////

    /**
     * Return true if we are not in the first firing and the sign of
     * some event indicator has changed.
     *
     * @return True if a state event has occurred.
     * @exception IllegalActionException If the fmiGetEventIndicators
     * function is missing, or if calling it does not return fmiOK.
     */
    private boolean _checkStateEvents() throws IllegalActionException {
        int number = _fmiModelDescription.numberOfEventIndicators;
        if (number == 0) {
            // No event indicators.
            return false;
        }
        if (_eventIndicators == null || _eventIndicators.length != number) {
            _eventIndicators = new double[number];
        }
        if (_fmiGetEventIndicatorsFunction == null) {
            throw new IllegalActionException(this, "Could not get the "
                    + _fmiModelDescription.modelIdentifier
                    + "_fmiGetEventIndicators"
                    + "() C function?  Perhaps the .fmu file \""
                    + fmuFile.asFile()
                    + "\" does not contain a shared library for the current "
                    + "platform?  ");
        }

        int fmiFlag = ((Integer) _fmiGetEventIndicatorsFunction.invoke(
                Integer.class, new Object[] { _fmiComponent, _eventIndicators,
                        new NativeSizeT(number) })).intValue();

        if (fmiFlag != FMILibrary.FMIStatus.fmiOK) {
            throw new IllegalActionException(this,
                    "Failed to get event indicators" + ", return result was "
                            + _fmiStatusDescription(fmiFlag));
        }

        if (_firstRound) {
            _eventIndicatorsPrevious = _eventIndicators;
            _eventIndicators = null;
            return false;
        }
        // Check for polarity change.
        for (int i = 0; i < number; i++) {
            if (_eventIndicatorsPrevious[i] * _eventIndicators[i] < 0.0) {
                return true;
            }
        }
        _eventIndicatorsPrevious = _eventIndicators;
        return false;
    }

    /**
     * Evaluate directional derivative with respect to inputs.
     *
     * @param idx The input index.
     * @param inputDerivatives The input derivatives.
     * @return The directional derivative with respect to inputs.
     * @exception IllegalActionException If an error when getting directional derivatives.
     */
    private double _evaluateInputDirectionalDerivatives(final int idx,
            final double[] inputDerivatives) throws IllegalActionException {
        return 0.0;
    }

    /**
     * Evaluate directional derivative with respect to states.
     *
     * @param idx The state derivative index.
     * @param stateDerivatives The state derivatives.
     * @return The directional derivative with respect to states.
     * @exception IllegalActionException If an error when getting directional derivatives.
     */
    private double _evaluateStateDirectionalDerivatives(final int idx,
            final double[] stateDerivatives) throws IllegalActionException {
        return 0.0;
    }
    /**
     * Get the indexes of the dependent inputs and continuous state variables.
     */
    private void _getStateDerivativesDependenciesIndexes() {
        // Get the number of continuous states.
        final int numContStates = _fmiModelDescription.numberOfContinuousStates;
        // Initialize arrays
        for (int i = 0; i < numContStates; i++) {
            // Initialize the lists.
            _fmiModelDescription.continuousStateDerivatives.get(i).dependentInputIndexes = new LinkedList<Integer>();
            _fmiModelDescription.continuousStateDerivatives.get(i).dependentStateIndexes = new LinkedList<Integer>();
            final FMI20ContinuousStateDerivative stateDerivative = _fmiModelDescription.continuousStateDerivatives
                    .get(i);
            // Get the indexes of the dependent input variables.
            for (int j = 0; j < _inputs.size(); j++) {
                final Input input = _inputs.get(j);
                if (stateDerivative.dependentScalarVariables
                        .contains(input.scalarVariable)) {
                    final int index = _inputs.indexOf(input);
                    _fmiModelDescription.continuousStateDerivatives.get(i).dependentInputIndexes
                            .add(index);
                }
            }
            // Get the indexes of dependent continuous state variables.
            for (int j = 0; j < numContStates; j++) {
                final ContinuousState state = _fmiModelDescription.continuousStates
                        .get(j);
                if (stateDerivative.dependentScalarVariables
                        .contains(state.scalarVariable)) {
                    final int index = _fmiModelDescription.continuousStates
                            .indexOf(state);
                    _fmiModelDescription.continuousStateDerivatives.get(i).dependentStateIndexes
                            .add(index);
                }
            }

        }
    }

    /**
     * Handle time, state and step events.
     *
     * @param timeValue The current time.
     * @exception IllegalActionException If an error occurs when handling events.
     */
    private void _handleEvents(double timeValue) throws IllegalActionException {
        // Complete the integrator step.
        // True if fmi2SetFMUState() will not be called for times
        // before the current time in this simulation.
        // Check event indicators.
        boolean stateEvent = _checkStateEvents();
        boolean noSetFMUStatePriorToCurrentPoint = true;
        boolean stepEvent = _fmiCompletedIntegratorStep(noSetFMUStatePriorToCurrentPoint);
        boolean timeEvent = ((_eventInfo.nextEventTimeDefined == 1) && (_eventInfo.nextEventTime < timeValue));

        if (timeEvent || stateEvent || stepEvent) {
            _enterEventMode();
            if (timeEvent) {
                // nTimeEvents++;
                if (_debugging) {
                    _debug("time event at t=" + timeValue);
                }
                if (stateEvent) {
                    if (_debugging) {
                        _debug("state event at t=" + timeValue);
                    }
                }
                if (stepEvent) {
                    // nStepEvents++;
                    // if (loggingOn) printf("step event at t=%.16g\n",
                    // time);
                    if (_debugging) {
                        _debug("step event at t=" + timeValue);
                    }
                }
                // "event iteration in one step, ignoring intermediate results"
                _eventInfo.newDiscreteStatesNeeded = (byte) 1;
                _eventInfo.terminateSimulation = (byte) 0;

                // FIXME: We assume no event iteration.
                final int fmiFlag = ((Integer) _fmiNewDiscreteStatesFunction
                        .invoke(Integer.class, new Object[] { _fmiComponent,
                                _eventInfo })).intValue();

                if (fmiFlag != FMILibrary.FMIStatus.fmiOK) {
                    throw new IllegalActionException(this,
                            "Failed to enter discrete state FMU: "
                                    + _fmiStatusDescription(fmiFlag));
                }
                if (_eventInfo.terminateSimulation == (byte) 1) {
                    System.out.println("model requested termination at t="
                            + timeValue);
                    getDirector().finish();
                }
                // Ingore event iteration and enter continuous mode.
                _enterContinuousTimeMode();

                // "check for change of value of states"
                if (_debugging) {
                    if (_eventInfo.valuesOfContinuousStatesChanged == (byte) 1) {
                        _debug("continuous state values changed at t="
                                + timeValue);
                    }
                    if (_eventInfo.nominalsOfContinuousStatesChanged == (byte) 1) {
                        _debug("nominals of continuous state changed  at t="
                                + timeValue);
                    }
                }
            }
        }
    }

    /**
     * Initialize the values of the continuous state ports with their
     * dependencies and remove the direct dependency of states on inputs.
     * 
     * @exception IllegalActionException If no port matching the name of 
     * a variable declared as an input is found.
     */
    private void _initializeContinuousStates() throws IllegalActionException {
        // Get the number of continuous states.
        final int numContStates = _fmiModelDescription.numberOfContinuousStates;
        // Initialize arrays
        _stateValueReferences = new long[numContStates];
        _stateDerivativeValueReferences = new long[numContStates];
        for (int i = 0; i < numContStates; i++) {
            _fmiModelDescription.continuousStates.get(i).port = (TypedIOPort) _getPortByNameOrDisplayName(_fmiModelDescription.continuousStates
                    .get(i).scalarVariable.name);
            final ContinuousState contState = _fmiModelDescription.continuousStates.get(i);
            final FMI20ContinuousStateDerivative contStateDeriv = _fmiModelDescription.continuousStateDerivatives.get(i);
            // Initialize vector of value references of state variables
            _stateValueReferences[i] = contState.scalarVariable.valueReference;
            // Initialize vector of value references of derivatives of state variables.
            _stateDerivativeValueReferences[i] = contStateDeriv.scalarVariable.valueReference;

            // Get the output retrieved from the model structure.
            Set<TypedIOPort> dependencies = null;
            for (int j = 0; j < contState.dependentScalarVariables
                    .size(); j++) {
                final String inputName = contState.dependentScalarVariables.get(j).name;
                final TypedIOPort inputPort = (TypedIOPort) _getPortByNameOrDisplayName(inputName);
                if (inputPort == null) {
                    continue;
                }
                if (dependencies == null) {
                    dependencies = new HashSet<TypedIOPort>();
                }
                dependencies.add(inputPort);
            }
        }
        
		// Iterate through the continuous state ports and remove their
		// dependencies with respect to inputs. There are no direct dependencies
		// between the continuous state ports and the input ports.
		for (int i = 0; i < _fmiModelDescription.numberOfContinuousStates; i++) {
			IOPort port = _fmiModelDescription.continuousStates.get(i).port;
			for (Input input : _getInputs()) {
				// Remove the dependency of the state output on the actor inputs
				_declareDelayDependency(input.port, port, 0.0);
			}
		}
    }

    /**
     * Initialize variable from type parameters.
     *
     * @exception IllegalActionException If the FMU cannot be initialized.
     */
    private void _initializeFMUParameters() throws IllegalActionException {

        // Set the parameters of the FMU.
        // Loop through the scalar variables and find a scalar
        // variable that has variability == "parameter" and is not an
        // input or output. We can't do this in attributeChanged()
        // because setting a scalar variable requires that
        // _fmiComponent be non-null, which happens in
        // preinitialize();
        // FIXME: This should probably also be done in attributeChanged(),
        // with checks that _fmiComponent is non-null, so that FMU parameters
        // can be changed during run time.
        for (FMIScalarVariable scalar : _fmiModelDescription.modelVariables) {
            if ((scalar.variability == FMIScalarVariable.Variability.parameter
                    || scalar.variability == FMIScalarVariable.Variability.fixed || scalar.variability == FMIScalarVariable.Variability.tunable) // FMI-2.0rc1
                    && scalar.causality != Causality.local // FMI-2.0rc1
                    && scalar.causality != Causality.input
                    && scalar.causality != Causality.output) {
                String sanitizedName = StringUtilities
                        .sanitizeName(scalar.name);
                Parameter parameter = (Parameter) getAttribute(sanitizedName,
                        Parameter.class);
                if (parameter != null) {
                    try {
                        // Only set parameters which are scalar variables
                        // Other parameters type will be ignored.
                        if (scalar.type instanceof FMIRealType) {                       
                        final double tmp_uu[] = {((DoubleToken) parameter.getToken()).doubleValue()};
                        final long tmp_uuRef[] = { scalar.valueReference };
                        // Set the inputs which have changed.
                        runNativeFMU(_fmiComponent, 7, null, null, null, 0.0, 0.0,
                                _director.getModelTime().getDoubleValue(), 0, 0.0, 0, 0, null, null, null, null, tmp_uu,
                                tmp_uuRef, null, null, 0, null, null, null, null, null);
                        }
                        //_setFMUScalarVariable(scalar, parameter.getToken());
                    } catch (IllegalActionException ex) {
                        throw new IllegalActionException(this, "Failed to set "
                                + scalar.name + " to " + parameter.getToken());
                    } catch (RuntimeException runtimeException) {
                        // FIXME: we are reusing supressWarnings here
                        // because the AMS model throws an exception
                        // while trying to set hx.hc.
                        if (!((BooleanToken) suppressWarnings.getToken())
                                .booleanValue()) {
                            throw new IllegalActionException(
                                    this,
                                    runtimeException,
                                    "Failed to set "
                                            + scalar.name
                                            + " to "
                                            + parameter.getToken()
                                            + ".  To ignore this exception, set the supressWarnings parameter.");
                        }
                    }
                }
            }
        }
    }

    /** Create a new QSS solver and initialize it for use by this actor.
     *  @exception IllegalActionException If the solver cannot be created or initialized.
     */
    private final void _createQSSSolver() throws IllegalActionException {


        final Time currentTime = _director.getModelTime();
        if (_debugging) {
            _debugToStdOut(String.format(
                    "FMUQSS._initializeQssIntegrator() on id{%d} at time %s",
                    System.identityHashCode(this), currentTime.toString()));
        }

        // Create a new QSS solver and initialize it.
        _qssSolver = _director.newQSSSolver();
        _qssSolver.initializeDerivativeFunction(this);
        _qssSolver.initializeSimulationTime(currentTime);
        _qssSolver.setQuantizationEventTimeMaximum(_director.getModelStopTime());
        
        // Get initial state values from FMU.
        // TODO: Does the FMUQSS need to carry {_states}? Shouldn't all state
        // information be delegated to the integrator {_qssIgr}?
        final int stateCt = _qssSolver.getStateCount();
        _states = new double[stateCt];
        
        // Get continuous states from FMU
        runNativeFMU(_fmiComponent, 4, null, null, null, 0.0, 0.0,
                currentTime.getDoubleValue(), 0, 0.0, 0, 0, null,
                null, _states, null, null, null, null, null, 0, null,
                null, null, null, null);
        
        // Set initial state values.
        for (int ii = 0; ii < stateCt; ++ii) {
            _qssSolver.setStateValue(ii, _states[ii]);
        }

        // Set quantization tolerances.
        double internalAbsoluteQuantum = _director.getAbsoluteQuantum() * _quantumScaleFactor;
        final double internalRelativeQuantum = _director.getRelativeQuantum() * _quantumScaleFactor;
        for (int ii = 0; ii < stateCt; ++ii) {
            // If the relativeQuantum is greater than 0.0, then use the
            // nominal value for the state, given by the FMU, to set the
            // absolute quantum. Specifically, if the relativeQuantum
            // times the nominal value is greater than the given absoluteQuantum,
            // then change the absoluteQuantum to be the product of the nominal
            // value and the relativeQuantum.
            // Thus a state with nominal value 1000 will have an absolute
            // quantum 1000 times greater than a state with nominal value 1.
            final double nominalValue = _fmiModelDescription.continuousStates
                    .get(ii).nominal.doubleValue();
            double modifiedAbsoluteQuantum = Math.abs(nominalValue) * internalRelativeQuantum;
            if (internalAbsoluteQuantum < modifiedAbsoluteQuantum) {
        	internalAbsoluteQuantum = modifiedAbsoluteQuantum;
            }
            _qssSolver.setQuantizationTolerance(ii, internalAbsoluteQuantum, internalRelativeQuantum);
        }

        // Tell integrator to quantize.
        // Expect this to fail-- integrator should want to do a rate-event also,
        // but don't yet have input variable models.
        //_triggerQuantizationEvents(currentTime, false); // TODO: Currently set
        // forceAll==false, to test code,
        // but should be true, to indicate
        // intent.

        // Diagnostic output.
        if (_debugging) {

            final int hashCode = System.identityHashCode(this);
            _debugToStdOut(String
                    .format("-- Id{%d} has input ports:", hashCode));
            int ii = 0;
            if (_qssSolver.getInputVariableCount() > 0) {
                for (Input input : _inputs) {
                    _debugToStdOut(String.format("-- %4d: %s", ii,
                            input.scalarVariable.name));
                    ++ii;
                }
            } else {
                _debugToStdOut("-- (None)");
            }

            _debugToStdOut(String.format(
                    "-- Id{%d} has output ports for quantized states:",
                    hashCode));
            for (ii = 0; ii < _qssSolver.getStateCount(); ++ii) {
                final TypedIOPort outPort = _fmiModelDescription.continuousStates
                        .get(ii).port;
                _debugToStdOut(String.format("-- %4d: %s", ii,
                        outPort.getName()));
            }

            _debugToStdOut(String.format(
                    "-- Id{%d} has output ports for FMU outputs:", hashCode));
            final List<Output> outputs = _outputs;
            if (outputs.size() > 0) {
                for (Output output : _outputs) {
                    // If the output port is the port of a quantized state
                    // variable,
                    // then skip it as this has already been set.
                    if (output.scalarVariable.isState) {
                        continue;
                    }
                    _debugToStdOut(String.format("-- %4d: %s", ii,
                            output.scalarVariable.name));
                    ++ii;
                }
            } else {
                _debugToStdOut("-- (None)");
            }

        }

    }

    /**
     * Configure the QSS integrator's input variable models.
     *
     * <p>This cannot be done during initialization stage, because did not yet have
     * all the information needed.</p>
     *
     * @param currentTime The current simulation time.
     */
    private final void _initializeQSSIntegratorInputVariables(
            final Time currentTime) throws IllegalActionException {

        if (_debugging) {
            _debugToStdOut(String
                    .format("FMUQSS._initializeQssIntegrator_inputVars() on id{%d} at time %s",
                            System.identityHashCode(this), currentTime.toString()));
        }

        // Create array for input models.
        final int ivCt = _qssSolver.getInputVariableCount();
        if (ivCt > 0) {
            _inputVariableModels = new ModelPolynomial[ivCt];
        }

        // Create input variable models.
        // Note this code block may have to change location at some point.
        // Reason-- when input variable models get variable order, will want to
        // read the model order from the model. Can't do that until have tokens.
        // But don't have tokens yet at this point in the initialization.
        final int ivMdlOrder = _qssSolver.getStateModelOrder();
        for (int ii = 0; ii < ivCt; ++ii) {
            // Get the input corresponding to input variable {ii}.
            final Input input = _inputs.get(ii);
            // Require port has known state.
            if (!input.port.isKnown(0)) {
                throw new IllegalActionException(this, String.format(
                        "Input port %s is unknown at initialization.",
                        input.port.getName()));
            }
            // Create the model.
            // Note the port may not have a token on it yet. Therefore,
            // while can create the model, can't initialize its time or value.
            final ModelPolynomial ivMdl = new ModelPolynomial(ivMdlOrder);
            _inputVariableModels[ii] = ivMdl;
            // ivMdl.tMdl = currentTime; // TODO: Confirm where time set.
            ivMdl.claimWriteAccess();
            // Give model to the integrator.
            _qssSolver.setInputVariableModel(ii, ivMdl);
        }

        // Load input variable models with data.
        for (int ii = 0; ii < ivCt; ++ii) {
            // Get the input corresponding to input variable {ii}.
            final Input input = _inputs.get(ii);
            double initialValue;
            // Look for a token.
            if (input.port.hasNewToken(0)) {
                final Token token = input.port.get(0);
                if (token instanceof SmoothToken) {
                    initialValue = ((SmoothToken) token).doubleValue();
                } else if (token instanceof DoubleToken) {
                    initialValue = ((DoubleToken) token).doubleValue();
                } else {
                    throw new IllegalActionException(this, String.format(
                            "Input port %s is connected to a port which has"
                                    + " a type which is not an instance "
                                    + "of SmoothToken or DoubleToken.",
                            input.port.getName()));
                }
            } else {
                // Here, missing a token.
                // TODO: This means there may be an algebraic loop. Need
                // to handle that as a real possibility.
                // Fill in a default value, taken from the FMU.
                // initialValue = input.scalarVariable.getDouble(_fmiComponent);
                initialValue = input.start;
                if (_debugging) {
                    _debugToStdOut(String
                            .format("-- Id{%d} set initial value of input %d to default value of %g",
                                    System.identityHashCode(this), ii,
                                    initialValue));
                }
            }
            // Get the model.
            // We only initialize the first coefficient
            // since we don't have other coefficients yet.
            final ModelPolynomial ivMdl = _inputVariableModels[ii];
            ivMdl.coeffs[0] = initialValue;
            ivMdl.tMdl = currentTime;
        }

        // Validate the integrator.
        final String failMsg = _qssSolver.validate();
        if (null != failMsg) {
            throw new IllegalActionException(this, failMsg);
        }

        // Set up integrator's state models.
        // TODO: Not sure this is necessary.
        _triggerQuantizationEvents(currentTime, false);
        try {
            // TODO: This may not be allowed-- rate events change internal
            // state,
            // which is meant to happen in postfire(). ??? Check Ptolemy user
            // guide.
            _qssSolver.triggerRateEvent();
        } catch (Exception ee) {
            // Rethrow as an IllegalActionException.
            throw new IllegalActionException(this, ee, "Triggering rate event failed.");
        }

    }
    
    /** Return the path to the JNI shared library which is used to interface with FMUs.
     *  @return The canonical path of the shared library.
     *  @exception IOException If thrown while determining the canonical path of the library.
     */
    private static String _jniPathSharedLibrary() throws IOException {

        // Determine the path to the shared object.
        // FIXME: There might be a better way of getting the JNI shared library.
        String topDirectory = System.getProperty("user.dir");
        String osName = System.getProperty("os.name").toLowerCase(
                Locale.getDefault());
        String extension = ".so";
        if (osName.startsWith("mac")) {
            osName = "darwin";
            extension = ".dylib";
        } else if (osName.startsWith("windows")) {
            osName = "win";
            extension = ".dll";
        }
        String bitWidth = "64";
        if (FMUFile.getIs32Bit()) {
            bitWidth = "32";
        }
        String library = topDirectory + File.separator + _jniLibPath
                + File.separator + osName + bitWidth + File.separator
                + _jniLibName + extension;
        File canonicalFile = new File(library).getCanonicalFile();
        if (!canonicalFile.exists()) {
            if (osName.startsWith("mac") || osName.startsWith("darwin")) {
                osName = "darwin-x86_";
                extension = ".so";
                library = topDirectory + File.separator + _jniLibPath
                        + File.separator + osName + bitWidth + File.separator
                        + _jniLibName + extension;
                File canonicalFile2 = new File(library).getCanonicalFile();
                if (canonicalFile2.exists()) {
                    System.out.println("Could not find " + canonicalFile
                            + " but " + canonicalFile2 + "exists.");
                    canonicalFile = canonicalFile2;
                } else {
                    System.out.println(canonicalFile + " does not exist"
                            + " also tried " + canonicalFile2);
                }
            }
        }
        String canonicalPath = canonicalFile.getCanonicalPath();
        return canonicalPath;
    }

    /**
     * Broadcast FMU outputs that are not quantized states.
     *
     * <p>FMU can produce outputs that don't correspond to states.</p>
     *
     * @param currentTime The current simulation time.
     */
    private final void _produceOutputs(final Time currentTime)
            throws IllegalActionException {

        if (_debugging) {
            _debugToStdOut(String.format(
                    "FMUQSS._produceOutputs() on id{%d} at time %s",
                    System.identityHashCode(this), currentTime.toString()));
        }
        // FIXME: This code will only work with doubles for now.
        for (int ii = 0; ii < _outputs.size(); ii++) {
            final Output output = _outputs.get(ii);

            final TypedIOPort port = output.port;
            // Skip known outputs and outputs which are states.
            // Remove isState check since we do not report outputs 
            // in triggerQuantizationEvents anymore.
            if ((_skipIfKnown() && port.isKnown(0)) || output.scalarVariable.isState) {
            // if ((_skipIfKnown() && port.isKnown(0))) {
                continue;
            }
                FMIScalarVariable scalarVariable = output.scalarVariable;

                if (scalarVariable.type instanceof FMIBooleanType) {
                    throw new IllegalActionException("Type "
                            + scalarVariable.type + " not supported.");
                    // scalarVariable.getBoolean(_fmiComponent);
                    // token = new BooleanToken(result);
                } else if (scalarVariable.type instanceof FMIIntegerType) {
                    // FIXME: handle Enumerations?
                    throw new IllegalActionException("Type "
                            + scalarVariable.type + " not supported.");
                    // int result = scalarVariable.getInt(_fmiComponent);
                    // token = new IntToken(result);
                } else if (scalarVariable.type instanceof FMIRealType) {
                    final long[] ooRef = { scalarVariable.valueReference };
                    final double[] oo = new double[1];
                    runNativeFMU(_fmiComponent, 8, null, null, null, 0.0, 0.0, 
                            currentTime.getDoubleValue(), 0,
                            0.0, 0, 1, null, null, null, null, null, null, oo, ooRef,
                            0, null, null, null, null, null);
                    // Send result to port.
                    _sendModelToPort(oo, port, currentTime, ii, false);
                } else if (scalarVariable.type instanceof FMIStringType) {
                    throw new IllegalActionException("Type "
                            + scalarVariable.type + " not supported.");
                    // String result = scalarVariable.getString(_fmiComponent);
                    // token = new StringToken(result);
                } else {
                    throw new IllegalActionException("Type "
                            + scalarVariable.type + " not supported.");
                }
        }

    }
    
    /**
     * Send output and state model to the port.
     *
     * @param val The values to be sent.
     * @param prt The port where values will be sent.
     * @param index The index of an output or continuous state.
     * @param isState The flag to indicate if continuous state.
     */
    private void _sendModelToPort(final double[] val,
            final TypedIOPort prt, Time time, int index, boolean isState)
            throws NoRoomException, IllegalActionException {

        double relativeQuantumUpscale = _internalRelativeQuantum
                / _quantumScaleFactor;
        // Handle outputs which are states.
        if (isState) {
            double lastToken = _fmiModelDescription.continuousStates.get(index).lastDoubleOutput;
            double epsilon = Math.abs(relativeQuantumUpscale * lastToken);
            if (_firstRound) {
                prt.send(0, new SmoothToken(val, time));
                _fmiModelDescription.continuousStates.get(index).lastDoubleOutput = val[0];
            } else {
                if (Math.abs(val[0] - lastToken) <= epsilon) {
                    return;
                } else {
                    prt.send(0, new SmoothToken(val, time));
                    _fmiModelDescription.continuousStates.get(index).lastDoubleOutput = val[0];
                }
            }
        }
        // Handle outputs which are not states.
        else {
            double lastToken = _outputs.get(index).lastDoubleOutput;
            double epsilon = Math.abs(relativeQuantumUpscale * lastToken);
            if (_firstRound) {
                prt.send(0, new SmoothToken(val, time));
                _outputs.get(index).lastDoubleOutput = val[0];
            } else {
                if (Math.abs(val[0] - lastToken) <= epsilon) {
                    return;
                } else {
                    prt.send(0, new SmoothToken(val, time));
                    _outputs.get(index).lastDoubleOutput = val[0];
                }
            }
        }
    }
    
    /** Populate the specified model with data from the specified token.
     *  If the token is a SmoothToken, then insert in the model any derivatives
     *  that might be given in the token, and set any remaining derivatives
     *  required by the model to zero. Otherwise, set any derivatives
     *  required by the model to zero.
     *  @param ivMdl The input model to parameterize.
     *  @param token The token values for parameterization.
     *  @throws IllegalActionException If the specified token cannot be converted
     *   to a double.
     */
	private void _setModelFromToken(ModelPolynomial ivMdl, Token token)
			throws IllegalActionException {

		// Convert to a DoubleToken. If token is a SmoothToken or DoubleToken,
		// then the convert method does nothing and just returns the token.
		// Otherwise, it attempts to convert it to a DoubleToken, and throws
		// an exception if such conversion is not possible.
		DoubleToken doubleToken = DoubleToken.convert(token);

		// In all cases, the first coefficient is simply the current value of
		// the token.
		ivMdl.coeffs[0] = doubleToken.doubleValue();

		double[] derivatives = null;
		if (doubleToken instanceof SmoothToken) {
			derivatives = ((SmoothToken) doubleToken).derivativeValues();
		}
		final int ncoeffs = ivMdl.coeffs.length;
		int factorial = 1;
		for (int i = 1; i < ncoeffs; i++) {
			if (derivatives == null || derivatives.length < i) {
				ivMdl.coeffs[i] = 0.0;
			} else {
				ivMdl.coeffs[i] = derivatives[i - 1]/factorial;
				factorial = factorial * i;
			}
		}
	}

    /**
     * Trigger quantization-events if necessary.
     *
     * <p>Update the external, quantized state models.</p>
     *
     * @param currentTime The current simulation time.
     * @param forceAll If true, requantize all state models.
     */
    private final void _triggerQuantizationEvents(final Time currentTime,
            final boolean forceAll) throws IllegalActionException {

        // Initialize.
        final int stateCt = _qssSolver.getStateCount();

        if (_debugging) {
            _debugToStdOut(String.format(
                    "FMUQSS._triggerQuantEvts() on id{%d} at time %s",
                    System.identityHashCode(this), currentTime.toString()));
        }

        // Loop over states that need to be requantized.
        int qIdx = -1;
        //final int order = _qssSolver.getStateModelOrder();
        while (true) {

            // Get next state.
            if (forceAll) {
                qIdx++;
                if (qIdx >= stateCt) {
                    break;
                }
            } else {
                qIdx = _qssSolver.needQuantizationEventIndex();
                if (qIdx < 0) {
                    break;
                }
            }

            // Requantize the state.
            _qssSolver.triggerQuantizationEvent(qIdx);

            // Export to rest of simulation.
            // TODO: Convert this to export models, not just values.
            final TypedIOPort outPort = _fmiModelDescription.continuousStates
                    .get(qIdx).port;

            // Report outputs at a discrete rate.
            // if ((_reportingTime == null) || (currentTime.equals(_reportingTime))){
                 _sendModelToPort(_qssSolver.getStateModel(qIdx).coeffs,
                        outPort, currentTime, qIdx, true);
            // }
                                        
            // Diagnostic output.
            if (_debugging) {
                _debugToStdOut(String.format(
                        "-- Id{%d} set quantized state model %d to %s", System
                                .identityHashCode(this), qIdx, _qssSolver
                                .getStateModel(qIdx).toString()));
            }

        }

        // A quantization-event implies other FMU outputs change.
        // Produce outputs to all outputs that do not depend on the states.
        // if ((_reportingTime == null) || (currentTime.equals(_reportingTime))){
        _produceOutputs(currentTime);
        // }
        
        // Advance the report time to the next value.
/*        if (_reportingTime == null){
            _reportingTime = currentTime.addUnchecked(_reportingTimePeriod);
        }
        else if (_reportingTime.equals(currentTime)){
            _reportingTime = _reportingTime.addUnchecked(_reportingTimePeriod);
        }*/
    }

    /**
     * Trigger a rate-event if necessary.
     *
     * <p>Update the internal, continuous state models.</p>
     *
     * @param currentTime The current simulation time.
     * @param force If true, always trigger a rate-event. Otherwise,
     * only trigger a rate-event if an input changed or if the
     * integrator signals it needs one to satisfy internal logic.
     */
    private final void _triggerRateEvent(final Time currentTime,
            final boolean force) throws IllegalActionException {

        if (_debugging) {
            _debugToStdOut(String.format(
                    "FMUQSS._triggerRateEvt() on id{%d} at time %s",
                    System.identityHashCode(this), currentTime.toString()));
        }

        // Update the input variable models if necessary.
        int curIdx = 0;
        boolean updatedInputVarMdl = false;
		for (Input input : _inputs) {
			// TODO: Check whether there is a guarantee that _getInputs() here
			// returns the same count of inputs as it did during
			// _initializeQssIntegrator_inputVars().
			// If not, then index {curIdx} can be wrong.
			assert (input.port.isKnown(0)); // Checked in
											// _initializeQssIntegrator_inputVars();
											// assume it doesn't change.
			if (input.port.hasNewToken(0)) {
				// Here, have a new value on the input port.
				final Token token = input.port.get(0);
				// Here we check whether we have an input distinct from previous one.
                // FIXME: I had to temporary remove this check which seems to 
                // be insufficient to determine whether we have distinct new inputs.
                // This was leading to a non-deterministic behavior of system models.
                // The drawback of this code is that it will always set inputs no matter whether
                // they have the same values.
				// if (!token.equals(input.lastInput)) {
					// Update the model.
					final ModelPolynomial ivMdl = _inputVariableModels[curIdx];
					// Set model from token.
					_setModelFromToken(ivMdl, token);
					// Update time.
					ivMdl.tMdl = currentTime;
					// Save the last token seen at this port
					// _inputs.get(curIdx).lastInput = token;
					// Set the hasChanged flag to true.
					_inputs.get(curIdx).hasChanged = true;
					if (_debugging) {
						_debugToStdOut(String.format(
								"-- Id{%d} set input variable model %d to %s",
								System.identityHashCode(this), curIdx,
								ivMdl.toString()));
					}
				// }
                updatedInputVarMdl = true;
			}
			curIdx++;
		}
        assert (_qssSolver.getInputVariableCount() == curIdx);

        // Trigger rate-event if necessary.
        if (force || updatedInputVarMdl || _qssSolver.needRateEvent()) {
            if (_debugging) {
                _debugToStdOut(String
                        .format("-- Id{%d} trigger rate-event because force=%b, updatedInputVarMdl=%b, _qssIgr.needRateEvt()=%b",
                                System.identityHashCode(this), force,
                                updatedInputVarMdl, _qssSolver.needRateEvent()));
            }
            try {
                _qssSolver.triggerRateEvent();
            } catch (Exception ee) {
                throw new IllegalActionException(this, ee, "Triggering rate event failed.");
            }
        }

    }

    ///////////////////////////////////////////////////////////////////
    ////                           private fields                  ////

    /** QSS director. */
    private QSSDirector _director;

    /** Buffer for event indicators. */
    private double[] _eventIndicators;

    /** Buffer for previous event indicators. */
    private double[] _eventIndicatorsPrevious;

    /** The eventInfo. */
    private FMI20EventInfo _eventInfo;

    /**
     * Flag identifying the first round of iterations of fire() and postfire()
     * after initialize().
     */
    private boolean _firstRound = true;
    
    /**
     * FMU instance index.
     */
    private int _fmiComponent;

    /** The inputs of this FMU. */
    private List<Input> _inputs;

    /** Models for communicating with the integrator. */
    private ModelPolynomial[] _inputVariableModels;
    
    /** Internal Relative Quantum. */
    private double _internalRelativeQuantum;
    
    /**
     * Indicator of whether the actor is strict, meaning that all inputs must be
     * known to fire it.
     */
    private boolean _isStrict = true;
    
    /** Name of the JNI library. */
    private final static String _jniLibName = "jniTofmu";
    
    /** Path to the JNI library. */
    private final static String _jniLibPath = "ptolemy/actor/lib/fmi/jni/fmuqss";

    /** Track requests for firing. */
    private Time _lastFireAtTime;
    
    /** Number of steps.  */
    private int _numberOfSteps;

    /** Vector of output value references.  */
    private long[] _outputValueReferences;

    /** The outputs of this FMU. */
    private List<Output> _outputs;
    
    /** The QSS solver for this actor.
     *  It is an instance of the class given by the
     *  <i>QSSSolver</i> parameter of the director.
     */
    private QSSBase _qssSolver = null;
    
    /** Quantum scale factor. */
    private double _quantumScaleFactor;
    
    /** Reporting time. */
    private Time _reportingTime = null;
    
    /** Reporting period. */
    private double _reportingTimePeriod = 900.0;
    
    /** Vector of state derivative value references. */
    private long[] _stateDerivativeValueReferences;
    
    /** The new states computed in fire, to be committed in postfire. */
    private double[] _states;
    
    /** Vector of state value references. */
    private long[] _stateValueReferences;
}
