import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class GatlingWeb extends Simulation {

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

    String csvFilePath = "data/user.csv";
    String CoachCSV = "data/coach.csv";
    String SeatCSV = "data/seatPref.csv";
    FeederBuilder.Batchable<String> csvFeeder = csv(csvFilePath).circular();
    FeederBuilder.Batchable<String> Coach = csv(CoachCSV).random();
    FeederBuilder.Batchable<String> Seat = csv(SeatCSV).random();

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
                            ))
            .pause(5)
            .exec(http("GotoTickets")
                    .get("/cgi-bin/welcome.pl?page=search").check(regex(" User has returned to the search page."))
                    .resources(http("TakeCities")
                                    .get("/cgi-bin/reservations.pl?page=welcome").check(regex("<option value=\"(.+?)\">").findAll().saveAs("cities")),
                            http("FlightsMenu")
                                    .get("/cgi-bin/nav.pl?page=menu&in=flights").check(regex("Web Tours Navigation Bar"))
                    )
            )
            .pause(5)
            .exec(session -> {
                @SuppressWarnings("unchecked")
                List<String> originalCities = (List<String>) session.get("cities");
                if (originalCities != null && originalCities.size() >= 2) {
                    List<String> cities = new ArrayList<>(originalCities);
                    Collections.shuffle(cities);

                    String departCity = cities.get(0);
                    String arriveCity = cities.get(1);

                    if (departCity.equals(arriveCity) && cities.size() > 2) {
                        arriveCity = cities.get(2);
                    }

                    System.out.println("Cities: " + cities);
                    System.out.println("-----departCity: " + departCity);
                    System.out.println("-----arriveCity: " + arriveCity);

                    session = session.set("departCity", departCity)
                            .set("arriveCity", arriveCity);
                }
                return session;
            })
            .exec(session -> {
                LocalDate today = LocalDate.now();
                LocalDate tomorrow = today.plusDays(1);
                LocalDate returnDate = tomorrow.plusMonths(1);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                String formattedTomorrow = tomorrow.format(formatter);
                String formattedReturnDate = returnDate.format(formatter);

                System.out.println("-----formattedTomorrow  " + formattedTomorrow);
                System.out.println("-----formattedReturnDate  " + formattedReturnDate);
                return session.set("departDate", formattedTomorrow)
                        .set("returnDate", formattedReturnDate);
            })
            .feed(Seat).feed(Coach)
            .exec(http("FormForTicket")
                    .post("/cgi-bin/reservations.pl").check(regex("name=\"outboundFlight\" value=\"(.+?)\"").findAll().saveAs("AirCraft")).check(regex("Flight departing from <B>#{departCity}</B> to <B>#{arriveCity}</B> "))
                    .headers(headers_2)
                    .formParam("advanceDiscount", "0")
                    .formParam("depart", "#{departCity}")
                    .formParam("departDate", "#{departDate}")
                    .formParam("arrive", "#{arriveCity}")
                    .formParam("returnDate", "#{returnDate}")
                    .formParam("numPassengers", "1")
                    .formParam("seatPref", "#{Seat}")
                    .formParam("seatType", "#{Coach}")
                    .formParam("findFlights.x", "21")
                    .formParam("findFlights.y", "7")
                    .formParam(".cgifields", "roundtrip")
                    .formParam(".cgifields", "seatType")
                    .formParam(".cgifields", "seatPref")
            )
            .pause(1).feed(Seat).feed(Coach)
            .exec(session -> {

                @SuppressWarnings("unchecked")
                List<String> airCrafts = (List<String>) session.get("AirCraft");
                System.out.println("-----Aircrafts" + airCrafts);
                if (airCrafts != null && !airCrafts.isEmpty()) {

                    int index = ThreadLocalRandom.current().nextInt(airCrafts.size());
                    String selectedAirCraft = airCrafts.get(index);
                    System.out.println("------selectedAirCraft " + selectedAirCraft);
                    session = session.set("selectedAirCraft", selectedAirCraft);
                }
                return session;
            })
            .pause(5)
            .feed(Seat).feed(Coach)
            .exec(http("ChooseAirCraft")
                    .post("/cgi-bin/reservations.pl").check(regex("Payment Details"))
                    .headers(headers_2)
                    .formParam("outboundFlight", "#{selectedAirCraft}")
                    .formParam("numPassengers", "1")
                    .formParam("advanceDiscount", "0")
                    .formParam("seatType", "#{Coach}")
                    .formParam("seatPref", "#{Seat}")
                    .formParam("reserveFlights.x", "26")
                    .formParam("reserveFlights.y", "9")
            )
            .pause(5).feed(Seat).feed(Coach)
            .exec(http("PassengerData")
                    .post("/cgi-bin/reservations.pl").check(regex(" #{departCity} to #{arriveCity}."))
                    .headers(headers_2)
                    .formParam("firstName", "#{First Name}")
                    .formParam("lastName", "#{Last Name}")
                    .formParam("address1", "#{street}")
                    .formParam("address2", "#{city/zipcode}")
                    .formParam("pass1", "#{First Name} #{Last Name}")
                    .formParam("creditCard", "#{card number}")
                    .formParam("expDate", "#{expiration}")
                    .formParam("oldCCOption", "")
                    .formParam("numPassengers", "1")
                    .formParam("seatType", "#{Coach}")
                    .formParam("seatPref", "#{Seat}")
                    .formParam("outboundFlight", "#{selectedAirCraft}")
                    .formParam("advanceDiscount", "0")
                    .formParam("returnFlight", "")
                    .formParam("JSFormSubmit", "off")
                    .formParam("buyFlights.x", "46")
                    .formParam("buyFlights.y", "4")
                    .formParam(".cgifields", "saveCC")
            )
            .pause(4)
            .exec( http("LogOut")
                    .get("/cgi-bin/welcome.pl?signOff=1").check(regex("Web Tours"))
                    .resources(
                            http("LogOutNavBar")
                                    .get("/cgi-bin/nav.pl?in=home").check(regex("Web Tours Navigation Bar"))
                    )
            );

    {
        setUp(scn.injectClosed(
                incrementConcurrentUsers(10)
                        .times(1)
                        .eachLevelLasting(Duration.ofSeconds(10))
                        .separatedByRampsLasting(Duration.ofSeconds(10))
                        .startingFrom(0),

                constantConcurrentUsers(10).during(Duration.ofMinutes(5)),

                incrementConcurrentUsers(10)
                        .times(1)
                        .eachLevelLasting(Duration.ofSeconds(10))
                        .separatedByRampsLasting(Duration.ofSeconds(10))
                        .startingFrom(10),

                constantConcurrentUsers(20).during(Duration.ofMinutes(5)),

                incrementConcurrentUsers(10)
                        .times(1)
                        .eachLevelLasting(Duration.ofSeconds(10))
                        .separatedByRampsLasting(Duration.ofSeconds(10))
                        .startingFrom(20),

                constantConcurrentUsers(30).during(Duration.ofMinutes(5))
        )).protocols(httpProtocol);
    }
}