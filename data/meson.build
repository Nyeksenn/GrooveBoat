desktop_file = i18n.merge_file(
        input: 'io.github.Nyeksenn.grooveboat.desktop.in',
       output: 'io.github.Nyeksenn.grooveboat.desktop',
         type: 'desktop',
       po_dir: '../po',
      install: true,
  install_dir: get_option('datadir') / 'applications'
)

desktop_utils = find_program('desktop-file-validate', required: false, disabler: true)
test('Validate desktop file', desktop_utils, args: [desktop_file])

appstream_file = i18n.merge_file(
        input: 'io.github.Nyeksenn.grooveboat.metainfo.xml.in',
       output: 'io.github.Nyeksenn.grooveboat.metainfo.xml',
       po_dir: '../po',
      install: true,
  install_dir: get_option('datadir') / 'metainfo'
)

appstreamcli = find_program('appstreamcli', required: false, disabler: true)
test('Validate appstream file', appstreamcli,
     args: ['validate', '--no-net', '--explain', appstream_file]
)

install_data('io.github.Nyeksenn.grooveboat.gschema.xml',
  install_dir: get_option('datadir') / 'glib-2.0' / 'schemas'
)

compile_schemas = find_program('glib-compile-schemas', required: false, disabler: true)
test('Validate schema file', compile_schemas,
     args: ['--strict', '--dry-run', meson.current_source_dir()]
)

resources = gnome.compile_resources(
  'io.github.Nyeksenn.grooveboat',
  'io.github.Nyeksenn.grooveboat.gresource.xml',
  gresource_bundle: true,
       install_dir: get_option('datadir') / 'io.github.Nyeksenn.grooveboat',
           install: true
)

subdir('icons')

service_conf = configuration_data()
service_conf.set('bindir', get_option('prefix') / get_option('bindir'))
configure_file(
          input: 'io.github.Nyeksenn.grooveboat.service.in',
         output: 'io.github.Nyeksenn.grooveboat.service',
  configuration: service_conf,
    install_dir: get_option('datadir') / 'dbus-1' / 'services'
)
