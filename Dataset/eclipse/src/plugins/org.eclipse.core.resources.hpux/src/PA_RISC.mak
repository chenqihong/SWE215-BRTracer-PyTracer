# makefile for PA_RISC libcore.sl

CORE.C = core.c
CORE.O = core.o
LIB_NAME = libcore.sl
LIB_NAME_FULL = libcore_3_1_0.sl

core :
	cc +z -c +O3 +DD32 +DS2.0 -I$(JDK_INCLUDE)/hp-ux -I$(JDK_INCLUDE) $(CORE.C) -o $(CORE.O)
	ld -b -o $(LIB_NAME_FULL) $(CORE.O) -lc

clean :
	rm *.o
