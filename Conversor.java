package com.conversor;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public class Conversor {

    static class ExchangeRateResponse {
        @SerializedName("result")
        String result;
        @SerializedName("base_code")
        String baseCode;
        @SerializedName("conversion_rates")
        Map<String, Double> conversionRates;
        @SerializedName("time_last_update_utc")
        String lastUpdated;
        @SerializedName("error-type")
        String errorType;
    }


    private static final Set<String> ALLOWED_CURRENCIES = Set.of("MXN", "BOB", "BRL", "CLP", "COP", "USD");
    private static final String API_KEY = "5f5385b02403aa1de7d6abdf";
    private static final String BASE_URL = "https://v6.exchangerate-api.com/v6";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            exibirMenu();
            String opcion = sc.nextLine().trim();

            if ("7".equals(opcion)) {
                System.out.println("Saliendo. ¡Gracias por usar el conversor!");
                break;
            }

            String from, to;
            switch (opcion) {
                case "1":
                    from = "USD";
                    to = "MXN";
                    break;
                case "2":
                    from = "MXN";
                    to = "USD";
                    break;
                case "3":
                    from = "USD";
                    to = "BRL";
                    break;
                case "4":
                    from = "BRL";
                    to = "USD";
                    break;
                case "5":
                    from = "USD";
                    to = "COP";
                    break;
                case "6":
                    from = "COP";
                    to = "USD";
                    break;
                default:
                    System.out.println("Opción inválida. Intenta de nuevo.");
                    continue;
            }

            System.out.printf("Ingresa la cantidad en %s: ", from);
            double amount;
            try {
                amount = Double.parseDouble(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Cantidad inválida. Vuelve a intentarlo.");
                continue;
            }

            try {
                BigDecimal resultado = convertirMoneda(from, to, amount);
                System.out.printf("%s %.4f = %s %s%n",
                        from, amount,
                        resultado.setScale(6, RoundingMode.HALF_UP).toPlainString(), to);
            } catch (Exception e) {
                System.out.println("Error en la conversión: " + e.getMessage());
            }

            System.out.println();
        }

        sc.close();
    }

    public static void exibirMenu() {
        System.out.println("""
                ****************************************************
                Sea bienvenido/a al Conversor de Moneda =]
                
                1) Dólar => Peso mexicano
                2) Peso mexicano => Dólar
                3) Dólar => Real brasileño
                4) Real brasileño => Dólar
                5) Dólar => Peso colombiano
                6) Peso colombiano => Dólar
                7) Salir
                Elija una opción válida:
                ****************************************************""");
    }

    public static BigDecimal convertirMoneda(String from, String to, double amount) throws IOException, InterruptedException {
        if (!ALLOWED_CURRENCIES.contains(from) || !ALLOWED_CURRENCIES.contains(to)) {
            throw new IllegalArgumentException("Moneda no permitida. Sólo se admiten: " + ALLOWED_CURRENCIES);
        }

        ExchangeRateResponse responseObj = fetchRates(from);
        Map<String, Double> filteredRates = filtrarConversionRates(responseObj.conversionRates);

        System.out.println("Tasas disponibles desde " + responseObj.baseCode + ":");
        filteredRates.forEach((code, val) -> System.out.printf("  %s = %.6f%n", code, val));

        if (!filteredRates.containsKey(to)) {
            throw new IOException("Tasa no disponible para: " + to + " (o no está en la lista permitida)");
        }

        double tasa = filteredRates.get(to);
        BigDecimal cantidad = BigDecimal.valueOf(amount);
        BigDecimal tasaBD = BigDecimal.valueOf(tasa);
        return cantidad.multiply(tasaBD);
    }

    private static ExchangeRateResponse fetchRates(String base) throws IOException, InterruptedException {
        String url = String.format("%s/%s/latest/%s", BASE_URL, API_KEY, base);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new IOException("Respuesta HTTP no exitosa: " + resp.statusCode());
        }

        Gson gson = new Gson();
        ExchangeRateResponse rateResponse = gson.fromJson(resp.body(), ExchangeRateResponse.class);

        if (rateResponse == null) {
            throw new IOException("No se pudo parsear la respuesta JSON.");
        }
        if (!"success".equalsIgnoreCase(rateResponse.result)) {
            String tipo = rateResponse.errorType != null ? rateResponse.errorType : "desconocido";
            throw new IOException("API devolvió error: " + tipo);
        }
        if (rateResponse.conversionRates == null) {
            throw new IOException("No se recibieron tasas de conversión.");
        }

        return rateResponse;
    }

    private static Map<String, Double> filtrarConversionRates(Map<String, Double> original) {
        if (original == null) return Map.of();
        return original.entrySet().stream()
                .filter(e -> ALLOWED_CURRENCIES.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
