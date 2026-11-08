The mapping bodies in logs.json, metrics.json, traces.json and services.json
are adapted from OpenSearch Data Prepper 2.12.2:
https://github.com/opensearch-project/data-prepper/tree/2.12.2/data-prepper-plugins/opensearch/src/main/resources

Copyright OpenSearch Contributors. Licensed under Apache License 2.0:
https://www.apache.org/licenses/LICENSE-2.0

ReconFlow adds index patterns, template priority, local shard settings, metadata,
keyword metric names for the native Metric Analytics picker,
stable keyword mappings for metric attributes, legacy log severity fields,
explicit trace rollover ownership, and double/long numeric mappings. The bootstrap installs these
composable templates before starting ingestion.
