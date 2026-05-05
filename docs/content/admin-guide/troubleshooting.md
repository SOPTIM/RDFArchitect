---
title: Troubleshooting
sidebar_position: 10
---

# Troubleshooting Basics

See the [FAQ](/reference/faq) for user-facing issues.

For operational issues:

- Backend logs are standard Spring Boot logs (log4j2), on stdout. Every REST call is logged at INFO level with the dataset, graph, and originating URL.
- Frontend build and runtime issues are reported in the browser console and in the SvelteKit server log.
- Triple store issues surface as HTTP errors in the backend log. A `Connection refused` means Fuseki is down or unreachable from the backend; a `403` means Fuseki is refusing the write (often because the dataset is configured as read-only on the Fuseki side).

For anything not covered here, the GitHub repository's [Discussions](https://github.com/SOPTIM/RDFArchitect/discussions) tab is the recommended channel.
