package br.ufv.vulnscan.port;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;

/**
 * Porta de saída para HTTP. Adaptador padrão: adapter/http/JdkHttpClient (java.net.http).
 * Em teste, entra um fake que devolve HttpResponse fixas, sem rede.
 */
public interface HttpClient {
    HttpResponse send(HttpRequest request);
}
