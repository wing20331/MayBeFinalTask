
import java.time.Duration;
import java.util.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import io.gatling.javaapi.jdbc.*;

import static io.gatling.core.Predef.heavisideUsers;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import static io.gatling.javaapi.jdbc.JdbcDsl.*;

public class gatlingWebTours extends Simulation {

    String url = "localhost:1080";

  private HttpProtocolBuilder httpProtocol = http
    .baseUrl("http://" + url)
    .inferHtmlResources(AllowList(), DenyList(".*\\.js", ".*\\.css", ".*\\.gif", ".*\\.jpeg", ".*\\.jpg", ".*\\.ico", ".*\\.woff", ".*\\.woff2", ".*\\.(t|o)tf", ".*\\.png", ".*\\.svg", ".*detectportal\\.firefox\\.com.*"))
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
    .upgradeInsecureRequestsHeader("1")
    .userAgentHeader("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0");
  
  private Map<CharSequence, String> headers_2 = Map.of("Origin", "http://localhost:1080");

    String csvFilePath = "data/user.csv"; // Путь к вашему CSV-файлу
    FeederBuilder.Batchable<  String> csvFeeder = csv(csvFilePath).circular();
  private ScenarioBuilder scn = scenario("gatlingWebTours")
    .exec(
      http("Index")
        .get("/cgi-bin/welcome.pl?signOff=true")
        .resources(
          http("SessionID")
            .get("/cgi-bin/nav.pl?in=home").check(regex("name=\"userSession\" value=\"(.+?)\"").saveAs("SessionID"))
        ),
      pause(5)
            .feed(csvFeeder),
      http("LogIN")
        .post("/cgi-bin/login.pl").check(regex("User password was correct"))
        .headers(headers_2)
        .formParam("userSession", "#{SessionID}")
        .formParam("username", "#{username}")
        .formParam("password", "#{password}")
        .formParam("login.x", "42")
        .formParam("login.y", "2")
        .formParam("JSFormSubmit", "off")
        .resources(
          http("NavBar")
            .get("/cgi-bin/nav.pl?page=menu&in=home").check(regex("WebTours/images/flights")),
          http("Welcome")
            .get("/cgi-bin/login.pl?intro=true").check(regex("Welcome, <b>#{username}</b>"))
        ),
      pause(2),
      http("LogOut")
        .get("/cgi-bin/welcome.pl?signOff=1").check(regex("Web Tours"))
        .resources(
          http("LogOutNavBar")
            .get("/cgi-bin/nav.pl?in=home").check(regex("Web Tours Navigation Bar"))
        )
    );

  {
	  setUp(scn.injectOpen(
              nothingFor(Duration.ofSeconds(5)),
              rampUsers(1).during(Duration.ofSeconds(10)),
              constantUsersPerSec(1).during(Duration.ofMinutes(3)),
              rampUsers(2).during(Duration.ofSeconds(10)),
              constantUsersPerSec(2).during(Duration.ofMinutes(3)),
              rampUsers(3).during(Duration.ofSeconds(10)),
              constantUsersPerSec(3).during(Duration.ofMinutes(3))
      )).protocols(httpProtocol);
  }
}
