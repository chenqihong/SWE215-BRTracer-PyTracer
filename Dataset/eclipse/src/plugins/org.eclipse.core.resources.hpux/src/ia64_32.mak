# makefile for ia64 libcore.so

CORE.C = core.c
CORE.O = core.o
LIB_NAME = libcore.so
LIB_NAME_FULL = libcore_3_1_0.so

core :
	cc +z -c +O3 +DD32 +DSblended -I$(JDK_INCLUDE)/hp-ux -I$(JDK_INCLUDE) $(CORE.C) -o $(CORE.O)
	ld -b -o $(LIB_NAME_FULL) $(CORE.O) -lc

clean :
	rm *.o
