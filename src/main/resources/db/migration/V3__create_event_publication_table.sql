CREATE TABLE event_publication
(
    id                     UUID          NOT NULL,
    completion_date        TIMESTAMPTZ,
    event_type             VARCHAR(512)  NOT NULL,
    listener_id            VARCHAR(512)  NOT NULL,
    publication_date       TIMESTAMPTZ   NOT NULL,
    serialized_event       VARCHAR(4000) NOT NULL,
    status                 VARCHAR(20),
    completion_attempts    INTEGER       NOT NULL,
    last_resubmission_date TIMESTAMPTZ,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_completion_date
    ON event_publication (completion_date);
