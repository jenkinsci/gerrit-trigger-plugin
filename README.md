# Gerrit Trigger Plugin

This plugin triggers builds on events from the Gerrit code review system by
retrieving events from the Gerrit command "stream-events", so the trigger is
pushed from Gerrit instead of pulled as scm-triggers usually are.

Various types of events can trigger a build, multiple builds can be triggered
by one event, and one consolidated report is sent back to Gerrit.

Multiple Gerrit server connections can be established per Jenkins instance.
Each job can be configured with one Gerrit server.


## Maintainers

* Robert Sandell
  - robert.sandell@cloudbees.com
  - sandell.robert@gmail.com

* Tomas Westling
  - tomas.westling@sonymobile.com


# Environments
* `linux`
    * `java-1.7`
        * `maven-3.0.4`

You should have no problem running the plugin on a Windows server.
The maintainers' development, tests and production environments are
Ubuntu 12.04 so we have no means of detecting or fixing any Windows issues,
but there are some kind contributors who provides win fixes every now and then.


# Build

The plugin depends on a [gerrit-events](https://github.com/sonyxperiadev/gerrit-events) component
that used to be part of this project but later broken out. Although we will try to avoid it,
sometimes you might need to _mvn install_ it locally if dependant changes there haven't been released yet.

The _(build-config)_ directory contains "special" CheckStyle configurations and the build will
fail during the verification phase if you don't follow them.

    mvn clean package
    
Run findbugs for future reference
or to make sure you haven't introduced any new warnings

    mvn findbugs:findbugs
    
# Test local instance

To test in a local Jenkins instance

    mvn hpi:run


# License

    The MIT License

    Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
    Copyright 2012 Sony Mobile Communications AB. All rights reserved.

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
    THE SOFTWARE.

