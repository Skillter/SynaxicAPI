package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.exception.GlobalExceptionHandler;
import dev.skillter.synaxic.model.dto.ColorConversionResponse;
import dev.skillter.synaxic.model.dto.ContrastRatioResponse;
import dev.skillter.synaxic.service.ApiKeyService;
import dev.skillter.synaxic.service.ConversionService;
import dev.skillter.synaxic.service.RateLimitService;
import dev.skillter.synaxic.util.IpExtractor;
import dev.skillter.synaxic.util.RequestLoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ColorConverterController.class)
@Import({GlobalExceptionHandler.class, ColorConverterControllerTest.ValidationConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class ColorConverterControllerTest {

    @TestConfiguration
    static class ValidationConfig {
        @Bean
        public MethodValidationPostProcessor methodValidationPostProcessor() {
            return new MethodValidationPostProcessor();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConversionService conversionService;

    @MockBean
    private ApiKeyService apiKeyService;

    @MockBean
    private RateLimitService rateLimitService;

    @MockBean
    private IpExtractor ipExtractor;

    @MockBean
    private RequestLoggingInterceptor requestLoggingInterceptor;

    @Test
    void convertColor_shouldReturnSuccess() throws Exception {
        ColorConversionResponse response = new ColorConversionResponse("#ffc107", "rgb(255, 193, 7)", "hsl(45, 100%, 51%)");
        when(conversionService.convertColor(anyString(), anyString(), anyString())).thenReturn(response);

        mockMvc.perform(get("/v1/color/convert")
                        .param("from", "hex")
                        .param("to", "all")
                        .param("value", "#ffc107"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hex", is("#ffc107")))
                .andExpect(jsonPath("$.rgb", is("rgb(255, 193, 7)")))
                .andExpect(jsonPath("$.hsl", is("hsl(45, 100%, 51%)")));
    }

    @Test
    void convertColor_shouldReturnBadRequestForMissingParam() throws Exception {
        mockMvc.perform(get("/v1/color/convert")
                        .param("from", "hex")
                        .param("to", "all"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Missing Required Parameter")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", containsString("value")));
    }

    @Test
    void convertColor_shouldReturnBadRequestForBlankParam() throws Exception {
        mockMvc.perform(get("/v1/color/convert")
                        .param("from", "hex")
                        .param("to", "all")
                        .param("value", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void getContrastRatio_shouldReturnSuccess() throws Exception {
        ContrastRatioResponse response = new ContrastRatioResponse(21.0, true, true);
        when(conversionService.calculateContrastRatio(anyString(), anyString())).thenReturn(response);

        mockMvc.perform(get("/v1/color/contrast")
                        .param("fg", "#000000")
                        .param("bg", "#ffffff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratio", is(21.0)))
                .andExpect(jsonPath("$.aa", is(true)))
                .andExpect(jsonPath("$.aaa", is(true)));
    }

    @Test
    void getContrastRatio_shouldReturnBadRequestForBlankParam() throws Exception {
        mockMvc.perform(get("/v1/color/contrast")
                        .param("fg", "#000000")
                        .param("bg", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void getContrastRatio_shouldReturnBadRequestForMissingParam() throws Exception {
        mockMvc.perform(get("/v1/color/contrast")
                        .param("fg", "#000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Missing Required Parameter")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", containsString("bg")));
    }
}