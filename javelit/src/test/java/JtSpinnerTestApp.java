//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.7-SNAPSHOT

import io.javelit.core.Jt;
import org.bsc.javelit.JtSpinner;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalUnit;

public class JtSpinnerTestApp {


    public static void main(String[] args) {

        var app = new JtSpinnerTestApp();

        app.view();
    }

    public void view() {
        Jt.title("JtSpinner test App").use();

        var sc = Jt.empty().key("spinner-container").use();

        JtSpinner.builder()
                .message("**this is the spinner test**")
                .loading(true)
                .showTime(true)
                .use(sc);

        try {
            Instant start = Instant.now();
            System.out.println( Jt.componentsState().getBoolean("spinner"));

            Thread.sleep(1000 * 10 );
            Duration duration = Duration.between(start, Instant.now());

            Jt.info("**Completed in** %ds".formatted(duration.toSeconds())).use(sc);

        } catch (InterruptedException e) {
            Jt.error( "interrupted exception");
            throw new RuntimeException(e);
        }
    }
}
