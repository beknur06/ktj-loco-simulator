package kz.ktj.digitaltwin.simulator.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${rabbitmq.exchange.telemetry}")
    private String exchangeName;

    /**
     * Topic exchange для телеметрии.
     * Routing key формат: telemetry.{locomotiveId}.{paramGroup}
     */
    @Bean
    public TopicExchange telemetryExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    /**
     * Jackson конвертер для автосериализации в JSON.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
