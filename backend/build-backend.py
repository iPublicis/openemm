#!/usr/bin/env python2
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
#
import	sys, os, getopt, time, stat
import	subprocess, fnmatch, tarfile
from	collections import defaultdict

filemapping = """
JAVA:
	lib/Mailout.ini

bin mode=0755:
	src/script/control/*
	src/script/tools/activator
	src/script/tools/script-tag
	src/script/tools/service.sh

	src/c/bav/bav
	src/c/tools/config-query
	src/c/tools/qctrl		user=root, group=root, mode=06755
	src/c/tools/smctrl		user=root, group=root, mode=06755
	src/c/tools/wrap
	src/c/xmlback/luatc
	src/c/xmlback/xmlback

lib:
	src/script/data/bav.rc		user=root, group=root, mode=0600
	src/script/data/bav.rule

scripts mode=0755:
	src/script/tools/activator.py
	src/script/lib/*.*
	src/script/process/*.py
	src/script/data/mailstatus.tmpl	mode=0644
	src/script/data/recovery.tmpl	mode=0644
	src/script/tools/service.py
	src/script/tools/service.cfg	mode=0644

scripts/once mode=0755:
	src/script/process/postproc.sh
	src/script/process/tag-install.sh
	
scripts/once/postproc mode=0755:
	src/script/process/postproc/*

scripts/once/tags:
	lib/tags/*.lua
"""

def toint (s):
	if type (s) is str:
		if s.lower ().startswith ('0x'):
			return int (s[2:], 16)
		if s.lower ().startswith ('0o'):
			return int (s[2:], 8)
		if s.startswith ('0'):
			return int (s, 8)
	return int (s)

class Builder (object):
	__slots__ = ['version', 'directory']
	def __init__ (self):
		self.version = None
		self.directory = None

	def run (self):
		print ('Build binaries')
		self.build_binaries ()
		print ('Determinate version')
		self.find_version ()
		print ('Create package')
		self.create_package ()
	
	def build_binaries (self):
		for module in 'lib', 'xmlback', 'bav', 'tools':
			self.build_binary (module)
	
	def build_binary (self, module):
		path = os.path.join ('src', 'c', module)
		self.call ('make', '-C', path, 'all')
	
	def find_version (self):
		xmlback = os.path.join ('src', 'c', 'xmlback', 'xmlback')
		pp = subprocess.Popen ([xmlback, '-V'], stdin = subprocess.PIPE, stdout = subprocess.PIPE, stderr = subprocess.PIPE)
		(out, err) = pp.communicate (None)
		if pp.returncode != 0:
			self.fail ('determinate version using %s results in %d (%s, %s)' % (xmlback, pp.returncode, out, err))
		version_parts = out.strip ().split ()
		if len (version_parts) == 0 or version_parts[-1] == '':
			self.fail ('failed to parse version response "%s" of %s' % (out, xmlback))
		self.version = version_parts[-1]
		self.directory = 'V%s' % self.version

	def create_package (self):
		(targets, target_options) = self.select_files ()
		#
		package = 'openemm-backend-%s.tar.gz' % self.version
		now = int (time.time ())
		with tarfile.open (package, 'w:gz') as tf:
			directories = sorted (targets.keys (), key = lambda p: (p.count ('/'), p))
			for directory in [self.directory] + [os.path.join (self.directory, _d) for _d in directories]:
				ti = tf.gettarinfo ('.', arcname = directory)
				ti.mode = stat.S_IFMT (ti.mode) | 0o755
				ti.uid = 500
				ti.gid = 500
				ti.uname = 'openemm'
				ti.gname = 'openemm'
				ti.mtime = now
				tf.addfile (ti)
			#
			for directory in directories:
				target_option = target_options[directory]
				for definition in targets[directory]:
					try:
						(source, option_string) = definition.split (None, 1)
					except ValueError:
						(source, option_string) = (definition, None)
					path = os.path.dirname (source)
					pattern = os.path.basename (source)
					options = self.parse_options (option_string, default = target_option)
					count = 0
					for filename in sorted (os.listdir (path)):
						if fnmatch.fnmatch (filename, pattern):
							count += 1
							filepath = os.path.join (path, filename)
							ti = tf.gettarinfo (filepath, arcname = os.path.join (self.directory, directory, filename))
							ti.mode = stat.S_IFMT (ti.mode) | toint (options.get ('mode', 0o644))
							ti.uname = options.get ('user', 'openemm')
							ti.gname = options.get ('group', 'openemm')
							ti.uid = toint (options.get ('uid', 0 if ti.uname == 'root' else 500))
							ti.gid = toint (options.get ('gid', 0 if ti.gname == 'root' else 500))
							with open (filepath, 'rb') as fd:
								tf.addfile (ti, fd)
					if count == 0:
						self.fail ('No files for %s/%s found' % (directory, definition))

	def select_files (self):
		targets = defaultdict (list)
		options = {}
		current = []
		for (no, line) in enumerate ((_l.rstrip () for _l in filemapping.split ('\n')), start = 1):
			pure = line.lstrip ()
			if not pure or pure.startswith ('#'):
				continue
			#
			if line[0] in (' ', '\t'):
				files = line.lstrip ()
				if current is None:
					self.fail ('%d: missing target directory for file definition: %s' % (no, files))
				current.append (files)
			elif line.endswith (':'):
				try:
					(target, option_string) = line[:-1].split (None, 1)
				except ValueError:
					(target, option_string) = (line[:-1], None)
				current = targets[target]
				options[target] = self.parse_options (option_string)
			else:
				self.fail ('%d: invalid line: %s' % (no, line))
		return (targets, options)

	def parse_options (self, option_string, default = None):
		options = {} if not default else default.copy ()
		if option_string:
			for element in (_e.strip () for _e in option_string.split (',')):
				(option, value) = [_p.strip () for _p in element.split ('=', 1)]
				options[option] = value
		return options

	def call (self, *args):
		rc = subprocess.call (list (args))
		if rc != 0:
			self.fail ('call to %s results in %d' % (' '.join (args), rc))

	def fail (self, message):
		sys.stderr.write ('failure: %s\n' % message)
		sys.exit (1)

def usage (error = None):
	sys.stderr.write (
		'Usage: %s\n'
		'Function: builds backend installations package\n'
		'Options:\n'
		'\tnone.\n'
		% sys.argv[0]
	)
	if error is not None:
		sys.stderr.write ('\n%s\n' % (error, ))
	sys.stderr.flush ()
	sys.exit (1)
	
def main ():
	try:
		(options, parameter) = getopt.getopt (sys.argv[1:], '')
		if parameter:
			raise Exception ('no command line parameter supported')
	except Exception as e:
		usage (e)
	build = Builder ()
	build.run ()

if __name__ == '__main__':
	main ()
