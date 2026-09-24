package br.edu.tcc.bola.openapi;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.List;

public class OpenApiParser {

    public record Endpoint(String path, String method, List<String> pathParams) {}

    public List<Endpoint> extractEndpoints(String specUrlOrFile) {
        OpenAPI api = new OpenAPIV3Parser().read(specUrlOrFile);
        List<Endpoint> result = new ArrayList<>();

        api.getPaths().forEach((path, item) -> {
            item.readOperationsMap().forEach((httpMethod, op) -> {
                List<String> params = extractPathParams(path);
                if (!params.isEmpty()) {
                    result.add(new Endpoint(path, httpMethod.name(), params));
                }
            });
        });
        return result;
    }

    private List<String> extractPathParams(String path) {
        List<String> params = new ArrayList<>();
        int i = 0;
        while ((i = path.indexOf('{', i)) != -1) {
            int end = path.indexOf('}', i);
            if (end > i) params.add(path.substring(i + 1, end));
            i = end + 1;
        }
        return params;
    }
}
