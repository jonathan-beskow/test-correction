package com.example.demo.service;

import com.example.demo.dto.ApontamentoDTO;
import com.example.demo.model.CasoCorrigido;
import com.example.demo.service.SimilaridadeService.CasoCorrigidoComSimilaridade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SugestaoCorrecaoService {

    @Autowired
    private VetorizacaoService vetorizacaoService;

    @Autowired
    private SimilaridadeService similaridadeService;

    public String sugerirCorrecao(ApontamentoDTO dto) {
        // 1. Gerar embedding com base em código + tipo
        List<Float> embedding = vetorizacaoService.gerarEmbedding(dto.getCodigo(), dto.getTipo());

        // 2. Buscar similar mais próximo no banco
        List<SimilaridadeService.CasoCorrigidoComSimilaridade> similares =
                similaridadeService.buscarSimilares(embedding, dto.getTipo());

        if (similares.isEmpty()) {
            return "Nenhum exemplo similar encontrado no banco.";
        }

        CasoCorrigido exemplo = similares.get(0).getCaso();

        // 3. Montar o payload com os campos adicionais
        Map<String, Object> payload = new HashMap<>();
        payload.put("codigo_alvo", dto.getCodigo());
        payload.put("tipo", dto.getTipo());
        payload.put("linguagem", dto.getLinguagem());
        payload.put("contexto", dto.getContexto());

        List<Map<String, String>> exemplos = new ArrayList<>();
        Map<String, String> exemploMap = new HashMap<>();
        exemploMap.put("codigo_original", exemplo.getCodigoOriginal());
        exemploMap.put("codigo_corrigido", exemplo.getCodigoCorrigido());
        exemploMap.put("linguagem", exemplo.getLinguagem());
        exemploMap.put("contexto", exemplo.getContexto());
        exemplos.add(exemploMap);

        payload.put("exemplos", exemplos);

        // 4. Enviar para o microserviço Python
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://microservico-embed:8000/gerar-correcao", entity, Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                Object correcao = response.getBody().get("codigoCorrigido");
                return correcao != null ? correcao.toString() : "Nenhuma correção gerada.";
            } else {
                return "Erro na requisição ao microserviço: " + response.getStatusCode();
            }
        } catch (Exception e) {
            return "Erro ao comunicar com o microserviço: " + e.getMessage();
        }
    }

}
