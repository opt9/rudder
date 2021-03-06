#####################################################################################
# Copyright 2014 Normation SAS
#####################################################################################
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, Version 3.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
#####################################################################################

bundle agent generic_process_check_process(binary, initscript, name) {

  vars:

      "c_initscript" string => canonify("${initscript}");

  methods:

      "start_process" usebundle => service_ensure_running_path("${initscript}", "${binary}");

      "any"           usebundle => rudder_common_reports_generic("server-roles", "service_ensure_running_${c_initscript}", "&TRACKINGKEY&", "Check ${name} process", "None", "Check ${name} process running");

}

bundle agent generic_process_check_bootstart(binary, initscript, name) {

  vars:

      "c_initscript" string => canonify("${initscript}");

  methods:

      "bootstart_service" usebundle => service_ensure_started_at_boot("${initscript}");
      "any" usebundle => rudder_common_reports_generic("server-roles", "service_ensure_started_at_boot_${c_initscript}", "&TRACKINGKEY&", "Check ${name} boot script", "None", "Check ${name} boot starting parameters");

}
