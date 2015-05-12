/* An actor that reads from images using Open Computer Vision (OpenCV)

 Copyright (c) 2010-2013 The Regents of the University of California.
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

package ptolemy.actor.lib.opencv;

import hypermedia.video.OpenCV;
import processing.core.PImage;

///////////////////////////////////////////////////////////////////
//// OpenCVImageObject

/**
 * An object that contains an OpenCV object combined with a PImage object.
 * @author Marc Geilen, Dorsa Sadigh, Steve Bako
 * @version $Id: OpenCVImageObject.java 67778 2013-10-26 15:50:13Z cxh $
 * @since Ptolemy II 7.1
 * @Pt.ProposedRating Red (cxh)
 * @Pt.AcceptedRating Red (cxh)
 */
public class OpenCVImageObject {

    // FIXME: rename this to OpenCVPImage.
    // 1) It should be PImage, not Image
    // 2) It is obviously an Object.

    /** Construct an object that contains an OpenCV object
     *  and a PImage object.
     *  @param openCVObject The OpenCV object.
     *  @param pImageObject The PImage object.
     */
    public OpenCVImageObject(OpenCV openCVObject, PImage pImageObject) {
        openCV = openCVObject;
        img = pImageObject;
    }

    /** The OpenCV object. */
    public OpenCV openCV;

    /** The PImage object. */
    public PImage img;
    // FIXME: Rename the above to pImage.  img does not match our naming conventions.
}
