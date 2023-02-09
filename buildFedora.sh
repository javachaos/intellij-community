#!/bin/bash
#########################################
# Install script for Fedora Silverblue  #
#########################################

sudo dnf install freetype fontconfig &&
git clone git://git.jetbrains.org/idea/android.git android &&
./installers.cmd -Dintellij.build.target.os=current
