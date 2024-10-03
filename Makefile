SHELL = /bin/bash

SDIR = src
ODIR = obj
_SDIR = $(SDIR)/gitlet
_ODIR = $(ODIR)/gitlet
OBJS = $(patsubst $(_SDIR)/%.java, $(_ODIR)/%.class, $(wildcard $(_SDIR)/*.java))
TARGET = gitlet.jar

$(TARGET): $(OBJS) $(ODIR)/manifest.txt
	cd $(ODIR) && jar cfm ../$(TARGET) manifest.txt gitlet

$(OBJS): $(_ODIR)/%.class : $(_SDIR)/%.java
	$(MAKE) -C $(SDIR)

$(ODIR)/manifest.txt: 
	echo "Manifest-Version: 1.0\nMain-Class: gitlet.Main" > $@

.PHONY: clean rm-repo test

clean:
	rm -rf $(_ODIR)/*.class gitlet.jar

rm-repo:
	rm -rf .gitlet/

test:
	cd $(SDIR)/test && python3 tester.py -c testfiles/*.in