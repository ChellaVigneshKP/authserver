package com.chellavignesh.authserver.recovery;
import com.chellavignesh.authserver.adminportal.certificate.CertificateEntity;
import com.chellavignesh.authserver.adminportal.certificate.CertificateStatus;
import com.chellavignesh.authserver.adminportal.certificate.dto.CertificateResponseDto;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.TimeZone;
import static org.assertj.core.api.Assertions.*;
class CertificateDisplayTest {
    @Test void laterTodayCertificateIsNotExpiredInEitherProjection() {
        var certificate=new CertificateEntity();
        certificate.setStatus(CertificateStatus.ACTIVE);
        certificate.setValidTo("2035-06-01 18:00:00");
        var clock=Clock.fixed(Instant.parse("2035-06-01T12:00:00Z"),ZoneOffset.UTC);
        var previous=TimeZone.getDefault();
        try {
            for(String zone:new String[]{"UTC","Asia/Kolkata","America/Los_Angeles"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertThat(CertificateResponseDto.fromCertificateEntityReduce(certificate,clock).isExpired()).as(zone).isFalse();
                assertThat(CertificateResponseDto.fromCertificateEntityExtended(certificate,clock).isExpired()).as(zone).isFalse();
            }
        } finally {TimeZone.setDefault(previous);}
    }
    @Test void fullUtcBoundaryIsInclusiveAndMalformedDatesFailClosed() {
        var certificate=new CertificateEntity();
        certificate.setStatus(CertificateStatus.ACTIVE);
        var end=Instant.parse("2035-06-01T18:00:00Z");
        var previous=TimeZone.getDefault();
        try {
            for(String zone:new String[]{"UTC","Asia/Kolkata","America/Los_Angeles"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                for(String value:new String[]{"2035-06-01 18:00:00.0000000","2035-06-01T18:00:00Z","2035-06-01T19:00:00+01:00"}) {
                    certificate.setValidTo(value);
                    for(Instant now:new Instant[]{end.minusSeconds(3600),end,end.plusMillis(1)}) {
                        var clock=Clock.fixed(now,ZoneId.of(zone));
                        assertThat(CertificateResponseDto.fromCertificateEntityReduce(certificate,clock).isExpired()).isEqualTo(now.isAfter(end));
                        assertThat(CertificateResponseDto.fromCertificateEntityExtended(certificate,clock).isExpired()).isEqualTo(now.isAfter(end));
                    }
                }
            }
            for(String invalid:new String[]{"", "malformed", "2035-02-30"}) {
                certificate.setValidTo(invalid);
                assertThat(CertificateResponseDto.fromCertificateEntityReduce(certificate,Clock.fixed(end,ZoneOffset.UTC)).isExpired()).isTrue();
                assertThat(CertificateResponseDto.fromCertificateEntityExtended(certificate,Clock.fixed(end,ZoneOffset.UTC)).isExpired()).isTrue();
            }
            certificate.setValidTo(null);
            assertThat(CertificateResponseDto.fromCertificateEntityReduce(certificate).isExpired()).isTrue();
        } finally {TimeZone.setDefault(previous);}
    }

}
