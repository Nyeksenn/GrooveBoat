package io.github.Nyeksenn.grooveboat;

import io.github.jwharm.javagi.base.GErrorException;
import io.github.jwharm.javagi.gtk.types.TemplateTypes;
import org.gnome.gio.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws GErrorException {

        var resource = Resource.load(
                "src/main/resources/grooveboat.gresource");
        resource.resourcesRegister();

        TemplateTypes.register(MusicChooser.class);

        new GrooveboatApp().run(args);
    }
}
