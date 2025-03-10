#!/bin/bash
####################################################################################################################################################################################################################################################################
#                                                                                                                                                                                                                                                                  #
#                                                                                                                                                                                                                                                                  #
#        Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)                                                                                                                                                                                                   #
#                                                                                                                                                                                                                                                                  #
#        This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.    #
#        This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.           #
#        You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.                                                                                                            #
#                                                                                                                                                                                                                                                                  #
####################################################################################################################################################################################################################################################################
#	-*- sh -*-
#
# Global configuration file for shell scripts on all production
# machines, including also several utility functions
#
# A.) Configuration
#
#
# Set the base for the whole system ..
if [ ! "$BASE" ] ; then
	BASE="$HOME"
fi
export BASE
#

export	SYSTEM_CONFIG='{
	"trigger-port": 8450
}'
export  DBCFG_PATH="$BASE/etc/dbcfg"
#

verbose=1
quiet=0
licence="`$BASE/bin/config-query licence:0`"
system="`uname -s`"
host="`uname -n | cut -d. -f1`"
optbase="$BASE/opt"
baselib=$BASE/lib
if [ ! -d $baselib ]; then
	mkdir $baselib
fi
# .. and for java ..
LC_ALL=C
NLS_LANG=american_america.UTF8
export LC_ALL LANG NLS_LANG
if [ ! "$JBASE" ] ; then
	JBASE="$BASE/JAVA"
fi
if [ ! "$JAVAHOME" ] ; then
	for java in "$optbase/software/java"; do
		if [ -d $java ] ; then
			for sdk in $java/*sdk* ; do
				if [ -d $sdk ] ; then
					JAVAHOME=$sdk
					break
				fi
			done
			if [ ! "$JAVAHOME" ] ; then
				JAVAHOME=$java
				break
			fi
		fi
	done
fi
if [ "$JAVAHOME" ] ; then
	PATH="$JAVAHOME/bin:$PATH"
	export PATH JAVAHOME
	for lib in $JAVAHOME/jre/lib/amd64/server/libjvm.so; do
		dst=$baselib/`basename $lib`
		if [ -f $lib ] && [ ! -f $dst ]; then
			ln -s $lib $dst
		fi
	done
fi
if [ "$JBASE" ] && [ -d $JBASE ] ; then
	cp="$JBASE"
	for jar in $JBASE/*.jar $JBASE/*.zip ; do
		if [ -f $jar ] ; then
			cp="$cp:$jar"
		fi
	done
	if [ "$CLASSPATH" ] ; then
		CLASSPATH="$CLASSPATH:$cp"
	else
		CLASSPATH="$cp"
	fi
fi
# .. and for oracle ..
if [ ! "$ORACLE_HOME" ] || [ ! -d "$ORACLE_HOME" ]; then
	for path in "/usr/lib/oracle/11.2/client64" \
		    "$optbase/software/oracle/product/11.2.0/client_1" \
		    "$optbase/software/oracle/product/10.2.0/client" \
		    "$optbase/software/oracle/software" \
		    "$optbase/software/oracle"
	do
		if [ -d $path ] && [ -d $path/lib ]; then
			ORACLE_HOME=$path
			export ORACLE_HOME
			for lpath in lib network/lib; do
				ldpath=$path/$lpath
				if [ -d $ldpath ]; then
					if [ "$LD_LIBRARY_PATH" ] ; then
						LD_LIBRARY_PATH="$ldpath:$LD_LIBRARY_PATH"
					else
						LD_LIBRARY_PATH="$ldpath"
					fi
				fi
			done
			if [ -d $path/bin ]; then
				PATH="$path/bin:$PATH"
			fi
			break
		fi
	done
fi
if [ "$ORACLE_HOME" ] && [ ! "$TNS_ADMIN" ]; then
	path="$optbase/etc/tnsnames.ora"
	if [ -f "$path" ]; then
		export TNS_ADMIN="`dirname $path`"
	fi
fi
# .. and for others ..
for other in python python2 python3 perl sqlite ; do
	path="$optbase/software/$other"
	if [ -d $path/bin ] ; then
		PATH="$path/bin:$PATH"
	fi
done
export PATH
#
# Logging
#
if [ "$LOG_HOME" ] ; then
	logpath="$LOG_HOME"
else
	logpath="$BASE/var/log"
fi
loghost="`uname -n | cut -d. -f1`"
logname="`basename -- $0`"
loglast=0
#
# MTA related
#
smctrl="$BASE/bin/smctrl"
sendmail="/usr/sbin/sendmail"
if [ ! -x $sendmail ] ; then
	sendmail="/usr/lib/sendmail"
fi
#
if [ ! "$MTA" ]; then
	MTA="`$BASE/bin/config-query mta`"
	if [ "$MTA" ]; then
		case "$MTA" in
		sendmail|postfix)
			;;
		*)
			echo "Invalid entry mta \"$MTA\" in system configuration detected, try to detect it by myself" 1>&2
			MTA=""
			;;
		esac
	fi
fi
if [ ! "$MTA" ]; then
	if [ -x $sendmail ]; then
		mta="`readlink -en $sendmail`"
		case "$mta" in
		*/sendmail.postfix)
			MTA="postfix"
			;;
		*/sendmail.sendmail)
			MTA="sendmail"
			;;
		esac
	fi
	if [ ! "$MTA" ]; then
		systemctl="`which systemctl`"
		if [ "$systemctl" ] && [ -x "$systemctl" ]; then
			for mta in sendmail postfix; do
				value="`$systemctl is-enabled ${mta}.service 2>/dev/null`"
				if [ "$value" = "enabled" ]; then
					MTA=$mta
					break
				fi
			done
		fi
	fi
	if [ ! "$MTA" ]; then
		count="`/bin/ps -ef | egrep 'postfix/(sbin/)?master' | grep -v grep|wc -l`"
		if [ $count -gt 0 ]; then
			MTA="postfix"
		else
			MTA="sendmail"
		fi
	fi
fi
if [ "$MTA" ]; then
	export	MTA
fi
if [ -x $smctrl ]; then
	sendmail=$smctrl
fi
#
SENDMAIL_DSN="`$BASE/bin/config-query enable-sendmail-dsn`"
if [ "$SENDMAIL_DSN" = "true" ]; then
	SENDMAIL_DSN_OPT=""
else
	SENDMAIL_DSN_OPT="-NNEVER"
fi
export SENDMAIL_DSN SENDMAIL_DSN_OPT
#
# B.) Routine collection
#
messagen() {
	if [ $verbose -gt 0 ] ; then
		case "$system" in
		SunOS|HP-UX)
			echo "$*\c"
			;;
		*)	echo -n "$*"
			;;
		esac
	fi
}
message() {
	if [ $verbose -gt 0 ] ; then
		echo "$*"
	fi
}
error() {
	if [ $quiet -eq 0 ]; then
		echo "$*" 1>&2
	fi
}
svcout() {
	if [ "$SVCFD" ]; then
		echo -n "$@" 1>&$SVCFD
		if [ $? -ne 0 ]; then
			export SVCFD=
		fi
	fi
}
epoch() {
	date '+%s'
}
log() {
	__fname="$logpath/`date +%Y%m%d`-${loghost}-${logname}.log"
	echo "[`date '+%d.%m.%Y  %H:%M:%S'`] $$ $*" >> $__fname
	loglast="`epoch`"
}
mark() {
	if [ $# -eq 1 ] ; then
		__dur=`expr $1 \* 60`
	else
		__dur=3600
	fi
	__now="`epoch`"
	if [ `expr $loglast + $__dur` -lt $__now ] ; then
		log "-- MARK --"
	fi
}
elog() {
	log "$*"
	error "$*"
}
mlog() {
	log "$*"
	message "$*"
}
die() {
	if [ $# -gt 0 ] ; then
		elog "$*"
	fi
	exit 1
}
mstart() {
	messagen "$* "
}
mproceed() {
	if [ $# -eq 0 ] ; then
		messagen "."
	else
		messagen " $* "
	fi
}
mend() {
	message " $*."
}
msleep() {
	if [ $# -ne 1 ] ; then
		__end=1
	else
		__end=$1
	fi
	__cur=0
	while [ $__cur -lt $__end ] ; do
		mproceed
		sleep 1
		__cur=`expr $__cur + 1`
	done
}
#
uid() {
	__uid="`id | tr ' ' '\n' | egrep '^uid=' | tr -cd '[0-9]'`"
	if [ ! "$__uid" ] ; then
		__uid="-1"
	fi
	echo "$__uid"
}
#
call() {
	if [ $# -eq 0 ] ; then
		error "Usage: $0 <program> [<parm>]"
		__rc=1
	else
		__tmp=/var/tmp/call.$$
		"$@" > $__tmp 2>&1
		__rc=$?
		cat $__tmp
		rm $__tmp
	fi
	return $__rc
}
#
pathstrip() {
	if [ $# -ne 1 ] ; then
		error "Usage: $0 <path>"
	else
		python -c "
def pathstrip (s):
	rc = []
	for e in s.split (':'):
		if not e in rc:
			rc.append (e)
	return ':'.join (rc)
print (pathstrip (\"$1\"))
"
	fi
}
#
getproc() {
	if [ $# -gt 0 ]; then
		local __pat __psopt __user __pids
		__pat="$1"
		if [ $# -gt 1 ] ; then
			__psopt="$2"
		else
			__psopt="-a"
		fi
		if [ "$as" ]; then
			__user="$as"
		else
			__user="`whoami`"
		fi
		if [ "$__user" = "-" ]; then
			__psopt="-e $__psopt"
		else
			__psopt="-u $__user $__psopt"
		fi
		__pids="`/bin/ps -f $__psopt | grep -- \"$__pat\" | grep -v grep | awk '{ print $2 }' | grep -v PID`"
		echo $__pids
	else
		echo "Usage: $0 <process-pattern> [<ps-opts>]" 1>&2
	fi
}
#
terminator() {
	while [ $# -gt 0 ] ; do
		local	__pat __run __sig
		__pat="$1"
		shift
		if [ "$__pat" ] ; then
			for __sig in 15 9 ; do
				__run="`getproc \"$__pat\"`"
				if [ "$__run" ] ; then
					messagen "Stop $__pat program with signal $__sig .. "
					kill -$__sig $__run >/dev/null 2>&1
					sleep 2
					message "done."
				fi
			done
		fi
	done
}
#
softterm() {
	while [ $# -gt 0 ] ; do
		local	__pat __repeat __run
		__pat="$1"
		shift
		if [ "$__pat" ] ; then
			for sv in 2 4 6 8 10 ; do
				__repeat="on"
				while [ $__repeat = "on" ]; do
					__repeat="off"
					__run="`getproc \"$__pat\"`"
					if [ "$__run" ] ; then
						messagen "Stop $__pat program  .. "
						kill -15 $__run >/dev/null 2>&1
						sleep 1
						__run="`getproc \"$__pat\"`"
						if [ "$__run" ]; then
							messagen "delaying $sv seconds .. "
							sleep `expr $sv - 1`
							if [ $sv -eq 10 ]; then
								__repeat="on"
							fi
						fi
						message "done."
					fi
				done
			done
		fi
	done
}
#
patternstatus() {
	local	__min __other __count
	if [ $# -gt 1 ]; then
		__min="$1"
		shift
	else
		__min=1
	fi
	if [ $# -gt 1 ]; then
		__other="$1"
		shift
	else
		__other=0
	fi
	__count="`getproc \"$@\" | wc -w`"
	if [ $__count -ge $__min ]; then
		echo "running"
	elif [ $__count -gt $__other ]; then
		echo "incomplete"
	else
		echo "stopped"
	fi
}
#
starter() {
	messagen "Start $* .. "
	(
		nohup "$@" > /dev/null 2>&1 &
	)
	message "done."
}
#
active() {
	local	__verbose=$verbose
	local	__quiet=$quiet
	
	verbose=0
	while getopts qv opt; do
		case "$opt" in
		q)
			if [ $verbose -eq 0 ]; then
				quiet=1
			else
				verbose=0
			fi
			;;
		v)
			if [ $quiet -eq 1 ]; then
				quiet=0
			else
				verbose=1
			fi
			;;
		esac
	done
	shift $((OPTIND-1))
	while [ $# -gt 0 ]; do
		service="$1"
		shift
		cmd="$BASE/bin/activator"
		if [ -x "$cmd" ]; then
			"$cmd" "$service"
			rc=$?
			case "$rc" in
			0)
				message "Service $service is marked as active."
				;;
			1)
				error "Service $service is marked as inactive."
				exit 0
				;;
			*)
				error "Service management for $service failed with $rc, aborting."
				exit $rc
				;;
			esac
		else
			error "No service management installed, $service requires it and will not start without it, aborting."
			exit 1
		fi
	done
	verbose=$__verbose
	quiet=$__quiet
}
#
if [ "$LD_LIBRARY_PATH" ] ; then
	LD_LIBRARY_PATH="$baselib:$LD_LIBRARY_PATH"
else
	LD_LIBRARY_PATH="$baselib"
fi
export LD_LIBRARY_PATH
LD_LIBRARY_PATH="`pathstrip \"$LD_LIBRARY_PATH\"`"
export LD_LIBRARY_PATH
#
if [ "$PATH" ] ; then
	PATH="$BASE/bin:$PATH"
else
	PATH="$BASE/bin"
fi
if [ "`uid`" = "0" ] && [ -d "$BASE/sbin" ]; then
	PATH="$BASE/sbin:$PATH"
fi
if [ -d "$BASE/lbin" ]; then
	PATH="$BASE/lbin:$PATH"
fi
PATH="`pathstrip \"$PATH\"`"
export PATH
#
if [ "$CLASSPATH" ] ; then
	CLASSPATH="`pathstrip \"$CLASSPATH\"`"
	export CLASSPATH
fi
#
if [ "$PYTHONPATH" ] ; then
	PYTHONPATH="$baselib:$PYTHONPATH"
else
	PYTHONPATH="$baselib"
fi
if [ -d "$BASE/plugins" ]; then
	PYTHONPATH="$BASE/plugins:$PYTHONPATH"
fi
PYTHONPATH="$BASE/scripts:$PYTHONPATH"
PYTHONPATH="`pathstrip \"$PYTHONPATH\"`"
export PYTHONPATH
#
LICENCE=$licence
export LICENCE
