SDIR = src
ODIR = obj
_SDIR = $(SDIR)/gitlet
_ODIR = $(ODIR)/gitlet
OBJS = $(patsubst $(_SDIR)/%.java, $(_ODIR)/%.class, $(wildcard $(_SDIR)/*.java))

all: $(OBJS) $(ODIR)/manifest.txt
	cd $(ODIR) && jar cfm ../gitlet.jar manifest.txt gitlet

$(OBJS):
	cd src && $(MAKE)

$(ODIR)/manifest.txt: 
	echo "Manifest-Version: 1.0\nMain-Class: gitlet.Main" > $@

.PHONY: clean
clean:
	rm -rf $(_ODIR)/*.class gitlet.jar