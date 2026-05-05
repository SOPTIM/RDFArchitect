---
title: Working with Namespaces
sidebar_position: 5
---

import manageNamespacesScreenshot from '@site/static/img/screenshots/manage-namespaces.png';

# Working with Namespaces

Namespaces are managed per dataset from **Edit → Manage Namespaces** (or **View Namespaces** when read-only). The dialog lists every prefix/URI pair currently defined for the dataset, and lets you add, rename, or delete prefixes.

<img src={manageNamespacesScreenshot} alt="Manage namespaces" className="screenshot--medium" />

Prefix uniqueness is enforced: the save button stays disabled while two rows share a prefix, and the offending rows are highlighted. Multiple prefixes may point to the same URI.

Namespaces are also surfaced in every place where a URI namespace is needed, such as the new class dialog and attribute editor, so you select a known namespace instead of typing it manually.
