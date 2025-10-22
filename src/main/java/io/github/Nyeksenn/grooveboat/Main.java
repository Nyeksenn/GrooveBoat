package io.github.Nyeksenn.grooveboat;

import io.github.jwharm.javagi.base.GErrorException;
import org.gnome.gio.Resource;

public class Main {

    public static void main(String[] args) throws GErrorException {
        var resource = Resource.load(
                "src/main/resources/grooveboat.gresource");
        resource.resourcesRegister();

        new GrooveboatApp().run(args);
    }
}
