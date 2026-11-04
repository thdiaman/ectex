#!/bin/sh
# Builds the EcTeX p2 update site (Linux/macOS launcher; the work is done by BuildSite.java).
#
#   sh build-site.sh --eclipse-home /opt/eclipse [--clean] [--qualifier TEXT]
#
# Instead of --eclipse-home you can set the ECLIPSE_HOME environment variable.
# Needs a JDK 17+ on the PATH. Run "sh build-site.sh --help" for all options.

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found on the PATH - install a JDK 17+ and try again." >&2
    exit 1
fi

here=$(cd "$(dirname "$0")" && pwd)
exec java -Dectex.build.dir="$here" "$here/BuildSite.java" "$@"
