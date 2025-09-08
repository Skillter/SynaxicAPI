package dev.skillter.synaxic.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoIpServiceTest {

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private DatabaseReader databaseReader;

    @InjectMocks
    private GeoIpService geoIpService;

    @BeforeEach
    void setUp() throws Exception {
        Resource mockResource = mock(Resource.class);
        InputStream mockInputStream = new ByteArrayInputStream(new byte[0]);
        when(resourceLoader.getResource(anyString())).thenReturn(mockResource);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(mockInputStream);

        geoIpService.init();
        ReflectionTestUtils.setField(geoIpService, "databaseReader", databaseReader);
    }

    @Test
    void getCountry_shouldReturnCountryCodeForValidIp() throws Exception {
        String ip = "8.8.8.8";
        CountryResponse mockResponse = mock(CountryResponse.class);
        Country mockCountry = mock(Country.class);
        when(mockCountry.getIsoCode()).thenReturn("US");
        when(mockResponse.getCountry()).thenReturn(mockCountry);
        when(databaseReader.country(InetAddress.getByName(ip))).thenReturn(mockResponse);

        Optional<String> country = geoIpService.getCountry(ip);

        assertThat(country).isPresent().contains("US");
    }

    @Test
    void getCountry_shouldReturnEmptyForIpNotFound() throws Exception {
        String ip = "127.0.0.1";
        when(databaseReader.country(any(InetAddress.class))).thenThrow(new AddressNotFoundException("Address not found"));

        Optional<String> country = geoIpService.getCountry(ip);

        assertThat(country).isEmpty();
    }

    @Test
    void getCountry_shouldReturnEmptyWhenDatabaseNotLoaded() {
        ReflectionTestUtils.setField(geoIpService, "databaseReader", null);
        Optional<String> country = geoIpService.getCountry("8.8.8.8");
        assertThat(country).isEmpty();
    }
}