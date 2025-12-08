package org.bsc.javelit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.javelit.core.JtComponent;
import io.javelit.core.JtComponentBuilder;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Language;

import java.io.StringWriter;

public class JtSpinner extends JtComponent<JtComponent.NONE> {

    private static final Mustache registerTemplate;
    private static final Mustache renderTemplate;

    static {
        final MustacheFactory mf = new DefaultMustacheFactory();
        registerTemplate = mf.compile("spinner.register.html.mustache");
        renderTemplate = mf.compile("spinner.render.html.mustache");
    }

    // visible to the template engine
    final boolean loading;
    final String message;
    final boolean showTime;
    final boolean overlay;

    /**
     * Builder for the spinner component.
     */
    public static class Builder extends JtComponentBuilder<NONE, JtSpinner, Builder> {
        private @Language("markdown") String message;
        private Boolean loading = false;
        private Boolean showTime = false;
        private Boolean overlay = false;

        /**
         * The message content to display.
         * Markdown is supported.
         */
        public Builder message(final @Language("markdown") @Nonnull String message) {
            this.message = message;
            return this;
        }

        /**
         * Whether the spinner is loading.
         * @param loading if true the spinner start.
         */
        public Builder loading( boolean loading) {
            this.loading = loading;
            return this;
        }

        /**
         * Whether to show the time.
         * @param showTime true to show the time.
         */
        public Builder showTime( boolean showTime) {
            this.showTime = showTime;
            return this;
        }

        /**
         * Whether to show the overlay.
         * @param overlay true to show component as an overlay.
         */
        public Builder overlay( boolean overlay) {
            this.overlay = overlay;
            return this;
        }

        @Override
        public JtSpinner build() {
            return new JtSpinner(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private JtSpinner( Builder builder ) {
        super(builder, NONE.NONE_VALUE, null);
        this.loading = builder.loading;
        this.message = markdownToHtml(builder.message, true);
        this.showTime = builder.showTime;
        this.overlay = builder.overlay;

    }

    @Override
    protected String register() {
        final StringWriter writer = new StringWriter();
        registerTemplate.execute(writer, this);
        return writer.toString();
    }

    @Override
    protected String render() {
        final StringWriter writer = new StringWriter();
        renderTemplate.execute(writer, this);
        return writer.toString();
    }

    @Override
    protected TypeReference<NONE> getTypeReference() {
        return new TypeReference<>() {
        };
    }

}
