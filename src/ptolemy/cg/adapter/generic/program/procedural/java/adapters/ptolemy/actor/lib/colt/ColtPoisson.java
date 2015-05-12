/* A helper class for ptolemy.actor.lib.colt.ColtPoisson

 @Copyright (c) 2005-2014 The Regents of the University of California.
 All rights reserved.

 Permission is hereby granted, without written agreement and without
 license or royalty fees, to use, copy, modify, and distribute this
 software and its documentation for any purpose, provided that the
 above copyright notice and the following two paragraphs appear in all
 copies of this software.

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
package ptolemy.cg.adapter.generic.program.procedural.java.adapters.ptolemy.actor.lib.colt;

import java.util.ArrayList;
import java.util.Set;

import ptolemy.cg.kernel.generic.program.CodeStream;
import ptolemy.kernel.util.IllegalActionException;

/**
 * An adapter class for ptolemy.actor.lib.colt.ColtPoisson.
 *
 * NOTE: Currently, random numbers produced by c code
 * generated by this class differ from those produced
 * by the ColtPoisson random generator.
 *
 * @see ptolemy.actor.lib.colt.ColtPoisson
 * @author Christopher Brooks, based on codegen version by Teale Fristoe
 * @version $Id: ColtPoisson.java 70398 2014-10-22 23:44:32Z cxh $
 * @since Ptolemy II 10.0
 * @Pt.ProposedRating Red (cxh)
 * @Pt.AcceptedRating Red (cxh)
 *
 */
public class ColtPoisson extends ColtRandomSource {
    /**
     * Constructor method for the ColtPoisson helper.
     * @param actor the associated actor
     */
    public ColtPoisson(ptolemy.actor.lib.colt.ColtPoisson actor) {
        super(actor);
    }

    ///////////////////////////////////////////////////////////////////
    ////                         public methods                    ////

    /** Generate the code for initializing the random number generator
     *  with the seed, if it has been given.  A seed of zero is interpreted
     *  to mean that no seed is specified.  In such cases, a seed based on
     *  the current time and this instance of a ColtRandomSource is used to be
     *  fairly sure that two identical sequences will not be returned.
     *  @return The initialize code of this actor.
     *  @exception IllegalActionException Not thrown in this class.
     */
    @Override
    public String generateInitializeCode() throws IllegalActionException {
        super.generateInitializeCode();
        CodeStream codeStream = _templateParser.getCodeStream();
        codeStream.appendCodeBlock("poissonInitBlock");

        return processCode(codeStream.toString());
    }

    /**
     * Get the files needed by the code generated for the
     * ColtPoisson actor.
     * @return A set of Strings that are names of the files
     *  needed by the code generated for the ColtPoisson actor.
     * @exception IllegalActionException Not Thrown in this subclass.
     */
    @Override
    public Set getHeaderFiles() throws IllegalActionException {
        Set files = super.getHeaderFiles();
        files.add("cern.jet.random.Poisson;");
        return files;
    }

    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////

    /** Generate code for producing a new random number.
     *  @exception IllegalActionException Not thrown in this base class.
     *  @return The code that produces a new random number.
     */
    @Override
    protected String _generateRandomNumber() throws IllegalActionException {
        ArrayList<String> args = new ArrayList<String>();
        return getTemplateParser().generateBlockCode(
                "poissonDistributionBlock", args);
    }
}