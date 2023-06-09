#*******************************************************************************
# Copyright (c) 2000, 2005 IBM Corporation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
# 
# Contributors:
#     IBM Corporation - initial API and implementation
#*******************************************************************************

# Makefile for creating SWT libraries for Linux GTK

include make_common.mak

SWT_VERSION=$(maj_ver)$(min_ver)

# Define the various shared libraries to be build.
WS_PREFIX = gtk
SWT_PREFIX = swt
AWT_PREFIX = swt-awt
SWTPI_PREFIX = swt-pi
CAIRO_PREFIX = swt-cairo
ATK_PREFIX = swt-atk
GNOME_PREFIX = swt-gnome
MOZILLA_PREFIX = swt-mozilla
SWT_LIB = lib$(SWT_PREFIX)-$(WS_PREFIX)-$(SWT_VERSION).so
AWT_LIB = lib$(AWT_PREFIX)-$(WS_PREFIX)-$(SWT_VERSION).so
SWTPI_LIB = lib$(SWTPI_PREFIX)-$(WS_PREFIX)-$(SWT_VERSION).so
CAIRO_LIB = lib$(CAIRO_PREFIX)-$(WS_PREFIX)-$(SWT_VERSION).so
ATK_LIB = lib$(ATK_PREFIX)-$(WS_PREFIX)-$(SWT_VERSION).so
GNOME_LIB = lib$(GNOME_PREFIX)-$(WS_PREFIX)-$(SWT_VERSION).so
MOZILLA_LIB = lib$(MOZILLA_PREFIX)-$(WS_PREFIX)-$(SWT_VERSION).so

CAIROCFLAGS = `pkg-config --cflags cairo`
CAIROLIBS = `pkg-config --libs-only-L cairo` -lcairo

# Do not use pkg-config to get libs because it includes unnecessary dependencies (i.e. pangoxft-1.0)
GTKCFLAGS = `pkg-config --cflags gtk+-2.0`
GTKLIBS = `pkg-config --libs-only-L gtk+-2.0 gthread-2.0` -lgtk-x11-2.0 -lgthread-2.0 -L$(XTEST_LIB_PATH) -lXtst

AWT_LIBS = -L$(AWT_LIB_PATH) -ljawt -shared -s

ATKCFLAGS = `pkg-config --cflags atk gtk+-2.0`
ATKLIBS = `pkg-config --libs-only-L atk gtk+-2.0`-latk-1.0 -lgtk-x11-2.0

GNOMECFLAGS = `pkg-config --cflags gnome-vfs-module-2.0 libgnome-2.0 libgnomeui-2.0`
GNOMELIBS = `pkg-config --libs-only-L gnome-vfs-module-2.0 libgnome-2.0 libgnomeui-2.0` -lgnomevfs-2 -lgnome-2 -lgnomeui-2

# Uncomment for Native Stats tool
#NATIVE_STATS = -DNATIVE_STATS

MOZILLACFLAGS = -O \
	-DXPCOM_GLUE=1 \
	-DMOZILLA_STRICT_API=1 \
	-fno-rtti \
	-fno-exceptions \
	-Wall \
	-DSWT_VERSION=$(SWT_VERSION) $(NATIVE_STATS) \
	-Wno-non-virtual-dtor \
	-fPIC \
	-I./ \
	-I$(JAVA_HOME)/include \
	-I$(JAVA_HOME)/include/linux \
	${GECKO_INCLUDES} \
	${SWT_PTR_CFLAGS}
	
MOZILLALIBS = -shared -s -Wl,--version-script=mozilla_exports -Bsymbolic ${GECKO_LIBS}
	
SWT_OBJECTS = swt.o callback.o
AWT_OBJECTS = swt_awt.o
SWTPI_OBJECTS = swt.o os.o os_structs.o os_custom.o os_stats.o
CAIRO_OBJECTS = swt.o cairo.o cairo_structs.o cairo_stats.o cairo_custom.o
ATK_OBJECTS = swt.o atk.o atk_structs.o atk_custom.o atk_stats.o
GNOME_OBJECTS = swt.o gnome.o gnome_structs.o gnome_stats.o
MOZILLA_OBJECTS = swt.o xpcom.o xpcom_custom.o xpcom_structs.o xpcom_stats.o
 
CFLAGS = -O -Wall \
		-DSWT_VERSION=$(SWT_VERSION) \
		$(NATIVE_STATS) \
		-DLINUX -DGTK \
		-I$(JAVA_HOME)/include \
		-I$(JAVA_HOME)/include/linux \
		-fpic \
		${SWT_PTR_CFLAGS}
LIBS = -shared -fpic -s


all: make_swt make_atk make_gnome make_awt

#
# SWT libs
#
make_swt: $(SWT_LIB) $(SWTPI_LIB)

$(SWT_LIB): $(SWT_OBJECTS)
	$(LD) $(LIBS) -o $(SWT_LIB) $(SWT_OBJECTS)

callback.o: callback.c callback.h
	$(CC) $(CFLAGS) -c callback.c

$(SWTPI_LIB): $(SWTPI_OBJECTS)
	$(LD) $(LIBS) $(GTKLIBS) -o $(SWTPI_LIB) $(SWTPI_OBJECTS)

swt.o: swt.c swt.h
	$(CC) $(CFLAGS) -c swt.c
os.o: os.c os.h swt.h os_custom.h
	$(CC) $(CFLAGS) $(GTKCFLAGS) -c os.c
os_structs.o: os_structs.c os_structs.h os.h swt.h
	$(CC) $(CFLAGS) $(GTKCFLAGS) -c os_structs.c 
os_custom.o: os_custom.c os_structs.h os.h swt.h
	$(CC) $(CFLAGS) $(GTKCFLAGS) -c os_custom.c
os_stats.o: os_stats.c os_structs.h os.h os_stats.h swt.h
	$(CC) $(CFLAGS) $(GTKCFLAGS) -c os_stats.c

#
# CAIRO libs
#
make_cairo: $(CAIRO_LIB)

$(CAIRO_LIB): $(CAIRO_OBJECTS)
	$(LD) $(LIBS) $(CAIROLIBS) -o $(CAIRO_LIB) $(CAIRO_OBJECTS)

cairo.o: cairo.c cairo.h swt.h
	$(CC) $(CFLAGS) $(CAIROCFLAGS) -c cairo.c
cairo_custom.o: cairo_custom.c cairo_structs.h cairo.h swt.h
	$(CC) $(CFLAGS) $(CAIROCFLAGS) -c cairo_custom.c
cairo_structs.o: cairo_structs.c cairo_structs.h cairo.h swt.h
	$(CC) $(CFLAGS) $(CAIROCFLAGS) -c cairo_structs.c
cairo_stats.o: cairo_stats.c cairo_structs.h cairo.h cairo_stats.h swt.h
	$(CC) $(CFLAGS) $(CAIROCFLAGS) -c cairo_stats.c

#
# AWT lib
#
make_awt:$(AWT_LIB)

$(AWT_LIB): $(AWT_OBJECTS)
	$(LD) $(AWT_LIBS) -o $(AWT_LIB) $(AWT_OBJECTS)

#
# Atk lib
#
make_atk: $(ATK_LIB)

$(ATK_LIB): $(ATK_OBJECTS)
	$(LD) $(LIBS) $(ATKLIBS) -o $(ATK_LIB) $(ATK_OBJECTS)

atk.o: atk.c atk.h
	$(CC) $(CFLAGS) $(ATKCFLAGS) -c atk.c
atk_structs.o: atk_structs.c atk_structs.h atk.h
	$(CC) $(CFLAGS) $(ATKCFLAGS) -c atk_structs.c
atk_custom.o: atk_custom.c atk_structs.h atk.h
	$(CC) $(CFLAGS) $(ATKCFLAGS) -c atk_custom.c
atk_stats.o: atk_stats.c atk_structs.h atk_stats.h atk.h
	$(CC) $(CFLAGS) $(ATKCFLAGS) -c atk_stats.c

#
# Gnome lib
#
make_gnome: $(GNOME_LIB)

$(GNOME_LIB): $(GNOME_OBJECTS)
	$(LD) $(LIBS) $(GNOMELIBS) -o $(GNOME_LIB) $(GNOME_OBJECTS)

gnome.o: gnome.c 
	$(CC) $(CFLAGS) $(GNOMECFLAGS) -c gnome.c

gnome_structs.o: gnome_structs.c 
	$(CC) $(CFLAGS) $(GNOMECFLAGS) -c gnome_structs.c
	
gnome_stats.o: gnome_stats.c gnome_stats.h
	$(CC) $(CFLAGS) $(GNOMECFLAGS) -c gnome_stats.c

#
# Mozilla lib
#
make_mozilla:$(MOZILLA_LIB)

$(MOZILLA_LIB): $(MOZILLA_OBJECTS)
	$(CXX) -o $(MOZILLA_LIB) $(MOZILLA_OBJECTS) $(MOZILLALIBS)

xpcom.o: xpcom.cpp
	$(CXX) $(MOZILLACFLAGS) -c xpcom.cpp

xpcom_structs.o: xpcom_structs.cpp
	$(CXX) $(MOZILLACFLAGS) -c xpcom_structs.cpp
	
xpcom_custom.o: xpcom_custom.cpp
	$(CXX) $(MOZILLACFLAGS) -c xpcom_custom.cpp

xpcom_stats.o: xpcom_stats.cpp
	$(CXX) $(MOZILLACFLAGS) -c xpcom_stats.cpp	

#
# Install
#
install: all
	cp *.so $(OUTPUT_DIR)

#
# Clean
#
clean:
	rm -f *.o *.so
