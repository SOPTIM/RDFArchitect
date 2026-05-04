---
title: Backups
sidebar_position: 5
---

# Backups

RDFArchitect keeps uploaded datasets and edits in backend memory. Fuseki backups cover snapshots only.

## Recommended routine

Fuseki has a built-in backup API:

```bash
curl -XPOST http://<fuseki-host>:3030/$/backup/<dataset>
```

This produces a gzipped N-Quads dump under Fuseki's `backups/` directory. A cron job that calls this once a day for each snapshot dataset and rotates the last N dumps covers snapshot recovery.

## Restoring

A dump produced by `/$/backup/<dataset>` is a standard N-Quads file and can be loaded back via Fuseki's data upload endpoint:

```bash
curl -XPOST -H 'Content-Type: application/n-quads' \
     --data-binary @backup.nq.gz \
     -H 'Content-Encoding: gzip' \
     http://<fuseki-host>:3030/<dataset>/data
```

## What to back up

- **Every snapshot dataset** in Fuseki.

## What does not need to be backed up

- The backend itself — stateless, can be rebuilt from the image.
- The frontend — stateless, can be rebuilt from the image.
- The gateway configuration — in version control.
- Uploaded datasets and edits — they live in backend memory and should be exported from RDFArchitect if they need to be kept outside the running session.
