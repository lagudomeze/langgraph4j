//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.7-SNAPSHOT

import io.javelit.core.Jt;
import org.bsc.javelit.JtSpinner;

import java.time.Duration;
import java.time.Instant;

public class JtSpinnerTestApp {


    public static void main(String[] args) {

        var app = new JtSpinnerTestApp();

        app.view();
    }

    public void view() {
        Jt.title("JtSpinner test App").use();

        var overlay = Jt.toggle("owerlay").value(false).use();

        var sc = Jt.empty().key("spinner-container").use();

        var spinnerBuilder = JtSpinner.builder()
                .key("spinner")
                .message("**this is the spinner test**")
                .loading(true)
                .showTime(true);
        if(overlay) {
            spinnerBuilder.overlay(true).use(sc);
        }
        else {
            spinnerBuilder.use(sc);
        }

        try {
            Instant start = Instant.now();

            Thread.sleep(1000 * 5 );
            Duration duration = Duration.between(start, Instant.now());

            Jt.info("**Completed in** %ds".formatted(duration.toSeconds())).use(sc);

        } catch (InterruptedException e) {
            Jt.error( "interrupted exception");
            throw new RuntimeException(e);
        }
    }
}
