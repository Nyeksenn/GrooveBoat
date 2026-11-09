package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.ui.CoverKind;
import io.github.Nyeksenn.grooveboat.ui.CoverLoader;
import io.github.Nyeksenn.grooveboat.ui.CoverSizes;
import org.gnome.gdk.Texture;
import org.gnome.glib.GLib;
import org.gnome.graphene.Rect;
import org.gnome.gsk.RoundedRect;
import org.gnome.gsk.ScalingFilter;
import org.gnome.gtk.CssProvider;
import org.gnome.gtk.Orientation;
import org.gnome.gtk.Snapshot;
import org.gnome.gtk.Widget;
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
    private String uuid;
    private CoverKind coverKind;
    private String coverId;
    /** Identifies the latest cover request, so a result that arrives for a recycled row is dropped. */
    private int coverRequest;

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
        queueDraw();
    }

    /**
     * Shows the album cover or artist image {@code id}, decoded at the smallest size level that covers this widget's
     * physical pixels. A {@code null} id clears the image. Main thread only; widgets are recycled by list views, so
     * a rebind simply calls this again.
     */
    public void setCover(CoverKind kind, String id) {
        var same = kind == coverKind && java.util.Objects.equals(id, coverId);
        this.coverKind = kind;
        this.coverId = id;
        if (id == null) {
            coverRequest++;
            setTexture(null);
            return;
        }
        if (!same) {
            // Don't show the previous row's image while the new one loads.
            setTexture(null);
        }
        refreshCover();
    }

    /** The device scale of the surface this widget is on; fractional on fractional-scaling desktops. */
    private double deviceScale() {
        var native_ = getNative();
        if (native_ != null) {
            var surface = native_.getSurface();
            if (surface != null) {
                return surface.getScale();
            }
        }
        return getScaleFactor();
    }

    /** Picks the size level for the current size and scale and loads it, showing a nearby level meanwhile. */
    private void refreshCover() {
        if (coverId == null) {
            return;
        }
        var kind = coverKind;
        var id = coverId;
        var logical = Math.max(pixelSize, Math.max(getWidth(), getHeight()));
        var bucket = CoverSizes.bucketFor(logical, deviceScale());
        var loader = CoverLoader.getInstance();

        var exact = loader.peekExact(kind, id, bucket);
        if (exact != null) {
            coverRequest++;
            setTexture(exact);
            return;
        }
        var fallback = loader.peekBest(kind, id, bucket);
        if (fallback != null) {
            setTexture(fallback);
        }
        var request = ++coverRequest;
        loader.loadAsync(kind, id, bucket).thenAccept(result -> GLib.idleAddOnce(() -> {
            if (request == coverRequest && result.isPresent()) {
                setTexture(result.get());
            }
        }));
    }

    @InstanceInit
    public void init() {
        // The scale changes when the window moves to another monitor; the size when the layout does.
        onNotify("scale-factor", _ -> refreshCover());
        onMap(this::refreshCover);
    }

    @Property(name = "radius")
    public float getRadius() {
        return radius;
    }

    @Property(name = "radius")
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

            // Textures come in size levels at most 2x the drawn size, which plain bilinear filtering handles. Only
            // a much larger one (a fallback level) needs mipmaps to avoid shimmering.
            var drawnPixels = scaledWidth * deviceScale();
            var filter = texWidth > 2 * drawnPixels ? ScalingFilter.TRILINEAR : ScalingFilter.LINEAR;
            snapshot.appendScaledTexture(texture, filter, textureRect);
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

    private void updateCss() {
        if (cssProvider != null) {
            var css =
                    String.format("""
                            .rounded-image-%s {
                                border-radius: %fpx;
                            }
                            """, uuid, getRadius());
            cssProvider.loadFromString(css);
        }
    }

    public RoundedImage() {
    }

    public RoundedImage(MemorySegment address) {
        super(address);
    }
}
