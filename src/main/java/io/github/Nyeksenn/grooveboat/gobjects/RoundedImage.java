package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gdk.Display;
import org.gnome.gdk.Texture;
import org.gnome.gobject.ParamSpec;
import org.gnome.graphene.Rect;
import org.gnome.gsk.RoundedRect;
import org.gnome.gsk.ScalingFilter;
import org.gnome.gtk.*;
import org.javagi.base.Out;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.Property;
import org.javagi.gobject.annotations.RegisteredType;

import java.lang.foreign.MemorySegment;

@RegisteredType(name = "RoundedImage")
public class RoundedImage extends Widget {
    private int pixelSize;
    private float radius = 5.0F;
    private Texture texture;
    private CssProvider cssProvider;

    public int getPixelSize() {
        return pixelSize;
    }

    public void setPixelSize(int pixelSize) {
        this.pixelSize = pixelSize;
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
    }

    @Property
    public float getRadius() {
        return radius;
    }

    @Property
    public void setRadius(float radius) {
        this.radius = radius;
    }

    @Override
    protected void snapshot(Snapshot snapshot) {
        if (texture != null) {
            float widgetWidth = (float) getWidth();
            float widgetHeight = (float) getHeight();
            float texWidth = (float) texture.getWidth();
            float texHeight = (float) texture.getHeight();

            if (texHeight == 0 || widgetHeight == 0) {
                return;
            }
            float texAspect = texWidth / texHeight;
            float widgetAspect = widgetWidth / widgetHeight;

            float scaledWidth, scaledHeight, xOffset, yOffset;

            if (texAspect > widgetAspect) {
                scaledWidth = widgetWidth;
                scaledHeight = widgetWidth / texAspect;
                xOffset = 0;
                yOffset = (widgetHeight - scaledHeight) / 2.0F;
            } else {
                scaledWidth = widgetHeight * texAspect;
                scaledHeight = widgetHeight;
                xOffset = (widgetWidth - scaledWidth) / 2.0F;
                yOffset = 0;
            }

            var textureRect = new Rect().init(xOffset, yOffset, scaledWidth, scaledHeight);
            var roundedRect = new RoundedRect().initFromRect(textureRect, radius);
            snapshot.pushRoundedClip(roundedRect);

            snapshot.appendScaledTexture(texture, ScalingFilter.TRILINEAR, textureRect);
            snapshot.pop();
        } else {
            super.snapshot(snapshot);
        }
    }

    @Override
    public void measure(Orientation orientation, int forSize, Out<Integer> minimum, Out<Integer> natural, Out<Integer> minimumBaseline, Out<Integer> naturalBaseline) {
        if (minimum == null) {
            return;
        }
        if (natural == null) {
            return;
        }
        if (minimumBaseline == null) {
            return;
        }
        if (naturalBaseline == null) {
            return;
        }

        if (orientation == Orientation.HORIZONTAL) {
            if (forSize != -1 && texture != null) {
                float texWidth = (float) texture.getWidth();
                float texHeight = (float) texture.getHeight();
                if (texHeight > 0) {
                    float aspect = texWidth / texHeight;
                    int naturalWidth = (int) (forSize * aspect);
                    minimum.set(pixelSize);
                    natural.set(naturalWidth);
                    minimumBaseline.set(-1);
                    naturalBaseline.set(-1);
                }
            } else {
                minimum.set(pixelSize);
                natural.set(pixelSize);
                minimumBaseline.set(-1);
                naturalBaseline.set(-1);
            }
        } else {
            if (forSize != -1 && texture != null) {
                float texWidth = (float) texture.getWidth();
                float texHeight = (float) texture.getHeight();
                if (texWidth > 0) {
                    float aspect = texHeight / texWidth;
                    int naturalHeight = (int) (forSize * aspect);
                    minimum.set(pixelSize);
                    natural.set(naturalHeight);
                    minimumBaseline.set(pixelSize);
                    naturalBaseline.set(pixelSize);
                }
            } else {
                minimum.set(pixelSize);
                natural.set(pixelSize);
                minimumBaseline.set(pixelSize);
                naturalBaseline.set(pixelSize);
            }
        }
    }

    private void updateCss(ParamSpec paramSpec) {
        if (cssProvider != null) {
            var css =
                    String.format("""
                            .rounded-image {
                                border-radius: %fpx;
                            }
                            """, radius);
            cssProvider.loadFromString(css);
        }
    }

    @InstanceInit
    public void init() {
        cssProvider = new CssProvider();
        addCssClass("rounded-image");
        Gtk.styleContextAddProviderForDisplay(Display.getDefault(), cssProvider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION);
        onNotify("radius", this::updateCss);
    }

    public RoundedImage() {

    }

    public RoundedImage(MemorySegment address) {
        super(address);
    }
}
