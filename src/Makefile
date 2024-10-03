SHELL = /bin/bash
JFLAGS = -g -Xlint:unchecked -Xlint:deprecation

SDIR = gitlet
ODIR = ../obj
_ODIR = $(ODIR)/gitlet
OBJS = $(patsubst $(SDIR)/%.java, $(_ODIR)/%.class, $(wildcard $(SDIR)/*.java))

all: $(OBJS)

$(OBJS): $(_ODIR)/%.class: $(SDIR)/%.java
	javac $(JFLAGS) $< -d $(ODIR)