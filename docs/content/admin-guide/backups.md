---
title: Backups
sidebar_position: 5
---

# Backups

RDFArchitect does not hold long-term state. Backup = Fuseki backup.

## Recommended routine

Fuseki has a built-in backup API:

```bash
curl -XPOST http://<fuseki-host>:3030/$/backup/<dataset>
```

This produces a gzipped N-Quads dump under Fuseki's `backups/` directory. A cron job that calls this once a day for each active dataset and rotates the last N dumps covers the standard disaster-recovery case.

## Restoring

A dump produced by `/$/backup/<dataset>` is a standard N-Quads file and can be loaded back via Fuseki's data upload endpoint:

```bash
curl -XPOST -H 'Content-Type: application/n-quads' \
     --data-binary @backup.nq.gz \
     -H 'Content-Encoding: gzip' \
     http://<fuseki-host>:3030/<dataset>/data
```

## What to back up

- **Every dataset** RDFArchitect knows about. In practice: `default`, plus every snapshot dataset.
- **Namespace and configuration state** is stored inside each dataset, so no separate backup is needed for it.

## What does not need to be backed up

- The backend itself — stateless, can be rebuilt from the image.
- The frontend — stateless, can be rebuilt from the image.
- The gateway configuration — in version control.
