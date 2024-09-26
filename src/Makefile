SDIR = gitlet
ODIR = ../obj
_ODIR = $(ODIR)/gitlet
OBJS = $(patsubst $(SDIR)/%.java, $(_ODIR)/%.class, $(wildcard $(SDIR)/*.java))

$(OBJS): $(_ODIR)/%.class: $(SDIR)/%.java
	javac $< -d $(ODIR)