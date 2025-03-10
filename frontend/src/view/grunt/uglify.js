var applicationFiles = [
  '<%= config.assets %>/js/vendor/jquery-1.11.1.js',
  '<%= config.assets %>/js/vendor/jquery-ui.min.1.11.1.js',
  '<%= config.assets %>/js/vendor/lodash-2.4.1.compat.js',
  '<%= config.assets %>/js/vendor/jquery-migrate-1.2.1.js',
  '<%= config.assets %>/js/vendor/jquery-i18n-1.1.1.js',
  '<%= config.assets %>/js/vendor/jquery-select2-3.5.2.js',
  '<%= config.assets %>/js/vendor/jquery-endless-scroll-1.6.0.js',
  '<%= config.assets %>/js/vendor/bootstrap-dropdown-3.2.0.js',
  '<%= config.assets %>/js/vendor/bootstrap-modal-3.2.0.js',
  '<%= config.assets %>/js/vendor/bootstrap-tooltip-3.2.0.js',
  '<%= config.assets %>/js/vendor/bootstrap-popover-3.2.0.js',
  '<%= config.assets %>/js/vendor/bootstrap-slider-4.8.1.js',
  '<%= config.assets %>/js/vendor/bootstrap-colorpicker-2.2.0.js',
  '<%= config.assets %>/js/vendor/d3.js',
  '<%= config.assets %>/js/vendor/c3.js',
  '<%= config.assets %>/js/vendor/toastr.custom.js',
  '<%= config.assets %>/js/vendor/moment-2.22.2.js',
  '<%= config.assets %>/js/vendor/moment-timezone-with-data-2012-2022.min.js',
  '<%= config.assets %>/js/vendor/pickadate-3.5.6.js',
  '<%= config.assets %>/js/vendor/pickadate-3.5.6.date.js',
  '<%= config.assets %>/js/vendor/pickadate-3.5.6.time.js',
  '<%= config.assets %>/js/vendor/pickadate-3.5.6.legacy.js',
  '<%= config.assets %>/js/vendor/ace_20.12.14/ace.js',
  '<%= config.assets %>/js/vendor/ace_20.12.14/ext-language_tools.js',
  '<%= config.assets %>/js/vendor/jquery-perfect-scrollbar-0.5.7.js',
  '<%= config.assets %>/js/vendor/jquery-mousewheel-3.1.9.js',
  '<%= config.assets %>/js/vendor/jquery-doublescroll-0.4.js',
  '<%= config.assets %>/js/vendor/iframe-resizer-host-window-2.7.1.js',
  '<%= config.assets %>/js/vendor/jquery-inputmask-3.1.62.js',
  '<%= config.assets %>/js/vendor/jquery-iframe-transport.js',
  '<%= config.assets %>/js/vendor/jquery-fileupload.9.11.2.js',
  '<%= config.assets %>/js/vendor/dragster.js',
  '<%= config.assets %>/js/vendor/emojionearea.js',
  '<%= config.assets %>/js/vendor/jcrop/jquery.color.js',
  '<%= config.assets %>/js/vendor/jcrop/jquery.Jcrop.js',
  '<%= config.assets %>/js/vendor/jQuery.extendext-0.1.1.js',
  '<%= config.assets %>/js/vendor/doT.js',
  '<%= config.assets %>/js/vendor/ag-grid-no-style-1.18.0.1.js',
  '<%= config.assets %>/js/boot/*.js',
  '<%= config.assets %>/js/vendor/querybuilder/query-builder.js',
  '<%= config.assets %>/js/lib/*.js',
  '<%= config.assets %>/js/lib/workflow/*.js',
  '<%= config.assets %>/js/lib/table/*.js',
  '<%= config.assets %>/js/modules/*.js',
  '<%= config.assets %>/js/initializers/*.js',
  '<%= config.assets %>/js/listener/*.js',
  '<%= config.assets %>/js/validators/*.js',
  '<%= config.assets %>/js/controllers/*.js',
  '<%= config.assets %>/js/vendor/slick.js',
  '<%= config.assets %>/js/vendor/interact.js'
],
birtFiles = [
  '<%= config.assets %>/js/vendor/iframe-resizer-content-window-2.7.1.js',
  '<%= config.assets %>/js/birt/*.js'
];

module.exports = {
  build: {
    files: {
      '<%= config.assets %>/application.js': applicationFiles,
      '<%= config.assets %>/birt.js':        birtFiles
    },
    options: {
      mangle: false,
      compress: false,
      beautify: true,
      sourcemap: true
    }
  },
  compile: {
    files: {
      '<%= config.assets %>/application.min.js': applicationFiles,
      '<%= config.assets %>/birt.min.js':        birtFiles
    },
    options: {
      mangle: true,
      compress: {},
      beautify: false,
      sourcemap: false
    }
  }
}

