package com.hermesworld.ais.galapagos.notifications.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.events.GalapagosEventContext;
import com.hermesworld.ais.galapagos.events.TopicEvent;
import com.hermesworld.ais.galapagos.events.TopicSchemaAddedEvent;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.notifications.NotificationParams;
import com.hermesworld.ais.galapagos.notifications.NotificationService;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.topics.SchemaMetadata;
import com.hermesworld.ais.galapagos.topics.TopicMetadata;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class NotificationEventListenerTest {

    private NotificationEventListener listener;

    private NotificationService notificationService;

    private GalapagosEventContext context;

    @Before
    public void feedMocks() {

        notificationService = spy(mock(NotificationService.class));
        KafkaClusters kafkaClusters = mock(KafkaClusters.class);
        ApplicationsService applicationsService = mock(ApplicationsService.class);
        TopicService topicService = mock(TopicService.class);
        CurrentUserService userService = mock(CurrentUserService.class);

        when(kafkaClusters.getProductionEnvironmentId()).thenReturn("prod");
        when(userService.getCurrentUserName()).thenReturn(Optional.of("testuser"));

        listener = new NotificationEventListener(notificationService, applicationsService, topicService, userService,
                kafkaClusters);

        context = mock(GalapagosEventContext.class);
        MockCluster cluster = new MockCluster("test");
        when(context.getKafkaCluster()).thenReturn(cluster.getCluster());
        KafkaClusters clusters = mock(KafkaClusters.class);

        when(clusters.getEnvironment("test")).thenReturn(Optional.of(cluster.getCluster()));
    }

    @Test
    public void testHandleTopicDeprecated() {
        AtomicInteger sendCalled = new AtomicInteger();
        when(notificationService.notifySubscribers(any(), any(), any(), any())).then(inv -> {
            sendCalled.incrementAndGet();
            return FutureUtil.noop();
        });

        listener.handleTopicDeprecated(buildTestEvent("test1"));
        listener.handleTopicDeprecated(buildTestEvent("test2"));
        listener.handleTopicDeprecated(buildTestEvent("prod"));

        assertEquals("Deprecation mail should only be sent for production environment", 1, sendCalled.get());
    }

    @Test
    public void testHandleTopicUndeprecated() {
        AtomicInteger sendCalled = new AtomicInteger();
        when(notificationService.notifySubscribers(any(), any(), any(), any())).then(inv -> {
            sendCalled.incrementAndGet();
            return FutureUtil.noop();
        });

        listener.handleTopicUndeprecated(buildTestEvent("test1"));
        listener.handleTopicUndeprecated(buildTestEvent("test2"));
        listener.handleTopicUndeprecated(buildTestEvent("prod"));

        assertEquals("Undeprecation mail should only be sent for production environment", 1, sendCalled.get());
    }

    @Test
    public void testHandleSchemaChangeDesc() throws ExecutionException, InterruptedException {

        TopicMetadata metadata = new TopicMetadata();
        metadata.setName("testtopic");
        metadata.setType(TopicType.EVENTS);

        SchemaMetadata schema = new SchemaMetadata();
        schema.setId("99");
        schema.setJsonSchema("{}");
        schema.setSchemaVersion(1);
        schema.setTopicName("testtopic");
        schema.setChangeDescription("some change description goes here");
        ArgumentCaptor<NotificationParams> captor = ArgumentCaptor.forClass(NotificationParams.class);
        when(notificationService.notifySubscribers(any(), any(), any(), any())).thenReturn(FutureUtil.noop());

        TopicSchemaAddedEvent event = new TopicSchemaAddedEvent(context, metadata, schema);
        listener.handleTopicSchemaAdded(event).get();

        verify(notificationService).notifySubscribers(eq("test"), eq("testtopic"), captor.capture(), any());

        NotificationParams params = captor.getValue();

        assertTrue(params.getVariables().get("change_action_text").toString()
                .contains("some change description goes here"));

    }

    private TopicEvent buildTestEvent(String envId) {
        GalapagosEventContext context = mock(GalapagosEventContext.class);
        KafkaCluster cluster = mock(KafkaCluster.class);
        when(cluster.getId()).thenReturn(envId);
        when(context.getKafkaCluster()).thenReturn(cluster);

        TopicMetadata metadata = new TopicMetadata();
        metadata.setName("topic-1");
        metadata.setType(TopicType.EVENTS);

        return new TopicEvent(context, metadata);
    }

    private static class MockCluster {

        private final KafkaCluster cluster;

        private final Map<String, TopicBasedRepositoryMock<?>> repositories = new HashMap<>();

        public MockCluster(String id) {
            cluster = mock(KafkaCluster.class);
            when(cluster.getId()).thenReturn(id);
            when(cluster.getRepository(any(), any())).then(
                    inv -> repositories.computeIfAbsent(inv.getArgument(0), key -> new TopicBasedRepositoryMock<>()));
        }

        public KafkaCluster getCluster() {
            return cluster;
        }
    }
}
