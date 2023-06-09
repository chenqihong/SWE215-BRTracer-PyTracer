# makefile for libcore.so

CORE.C = core.c
CORE.O = core.o
LIB_NAME = libcore.so
LIB_NAME_FULL = libcore_2_1_0b.so

core :
	qcc -Wall -O3 -shared -c -I$(IVE_HOME)/include $(CORE.C) -o $(CORE.O)
	qcc -Wall -O3 -shared -Wl,-soname,$(LIB_NAME) -o $(LIB_NAME_FULL) $(CORE.O)

clean :
	rm -f *.o *.so
