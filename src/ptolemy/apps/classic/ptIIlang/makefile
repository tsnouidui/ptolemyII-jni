# Makefile for ptIIlang, which uses Ptolemy Classic ptlang to generate ptolemy II files
#
# @Version: $Id: makefile 36201 2005-02-23 00:00:34Z cxh $
# @Author: Christopher Brooks
#
# This directory contains the matlab engine interface
#
# @Copyright (c) 2005 The Regents of the University of California
# All rights reserved.
#
# Permission is hereby granted, without written agreement and without
# license or royalty fees, to use, copy, modify, and distribute this
# software and its documentation for any purpose, provided that the
# above copyright notice and the following two paragraphs appear in all
# copies of this software.
#
# IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA  OR RESEARCH IN MOTION
# LIMITED BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL,
# OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE USE OF THIS SOFTWARE AND
# ITS DOCUMENTATION, EVEN IF THE UNIVERSITY OF CALIFORNIA OR
# RESEARCH IN MOTION LIMITED HAVE BEEN ADVISED OF THE POSSIBILITY OF
# SUCH DAMAGE.
#
# THE UNIVERSITY OF CALIFORNIA AND RESEARCH IN MOTION LIMITED
# SPECIFICALLY DISCLAIMS ANY WARRANTIES,
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
# PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
# CALIFORNIA  AND RESEARCH IN MOTION LIMITED
# HAVE NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
# ENHANCEMENTS, OR MODIFICATIONS.
#
# 						PT_COPYRIGHT_VERSION_2
# 						COPYRIGHTENDKEY
##########################################################################

ME =		ptolemy/apps/classic/ptIIlang

DIRS =

# Root of the Java directory
ROOT =		../../../..

CLASSPATH =	$(ROOT)

# Get configuration info
CONFIG =	$(ROOT)/mk/ptII.mk
include $(CONFIG)

# Used to build jar files
PTPACKAGE = 	ptIIlang
PTCLASSJAR = 	$(PTPACKAGE).jar

# Keep this list alphabetized.
JSRCS =

OTHER_FILES_TO_BE_JARED = 


EXTRA_SRCS =	$(JSRCS) $(OTHER_FILES_TO_BE_JARED) \
	ptlang.c \
	ptlang.y

# Sources that may or may not be present, but if they are present, we don't
# want make checkjunk to barf on them.
# Don't include demo or DIRS here, or else 'make sources' will run 'make demo'
MISC_FILES =


# make checkjunk will not report OPTIONAL_FILES as trash
# make distclean removes OPTIONAL_FILES
OPTIONAL_FILES = \

# make clean removes $(KRUFT)
KRUFT = 

all: ptIIlang
	ptIIlang CGCFastFIR.pl

#	ptIIlang /users/ptolemy/src/domains/cgc/stars/CGCFix.pl
#	ptIIlang /users/ptolemy/src/domains/cgc/dsp/stars/CGCFastFIR.pl


jclass: $(PTLIB)


ptIIlang.c: ptIIlang.y
	$(YACC) ptIIlang.y; mv y.tab.c ptIIlang.c

ptIIlang: ptIIlang.c
	$(PTCC) -g ptIIlang.c -o ptIIlang

