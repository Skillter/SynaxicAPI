package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.ByteConversionResponse;
import dev.skillter.synaxic.model.dto.UnitConversionResponse;
import dev.skillter.synaxic.security.ApiKeyAuthFilter;
import dev.skillter.synaxic.security.RateLimitFilter;
import dev.skillter.synaxic.service.ConversionService;
import dev.skillter.synaxic.util.IpExtractor;
import dev.skillter.synaxic.util.RequestLoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.MockBeans;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UnitConverterController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
@Import(IpExtractor.class)
@MockBeans({
        @MockBean(ApiKeyAuthFilter.class),
        @MockBean(RateLimitFilter.class),
        @MockBean(RequestLoggingInterceptor.class)
})
class UnitConverterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConversionService conversionService;

    @Test
    void convertUnits_shouldReturnSuccess() throws Exception {
        UnitConversionResponse response = new UnitConversionResponse("mi", "km", 3.1, 4.9889664);
        when(conversionService.convertUnits(anyString(), anyString(), anyDouble())).thenReturn(response);

        mockMvc.perform(get("/v1/convert/units")
                        .param("from", "mi")
                        .param("to", "km")
                        .param("value", "3.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from", is("mi")))
                .andExpect(jsonPath("$.result", is(4.9889664)));
    }

    @Test
    void convertUnits_shouldReturnBadRequestForMissingParam() throws Exception {
        mockMvc.perform(get("/v1/convert/units")
                        .param("from", "mi")
                        .param("value", "3.1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void convertBytes_shouldReturnSuccess() throws Exception {
        BigDecimal val = new BigDecimal("128");
        BigDecimal res = new BigDecimal("134.217728");
        BigDecimal ratio = new BigDecimal("1.048576");

        ByteConversionResponse response = new ByteConversionResponse("MiB", "MB", val, res, ratio);
        when(conversionService.convertBytes(anyString(), anyString(), any(BigDecimal.class))).thenReturn(response);

        mockMvc.perform(get("/v1/convert/bytes")
                        .param("from", "MiB")
                        .param("to", "MB")
                        .param("value", "128"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from", is("MiB")))
                .andExpect(jsonPath("$.value", is(128)))
                .andExpect(jsonPath("$.result", is(134.217728)));
    }

    @Test
    void convertBytes_shouldReturnBadRequestForNonPositiveValue() throws Exception {
        mockMvc.perform(get("/v1/convert/bytes")
                        .param("from", "MiB")
                        .param("to", "MB")
                        .param("value", "0"))
                .andExpect(status().isBadRequest());
    }
}