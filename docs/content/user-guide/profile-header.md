---
title: The Profile Header
sidebar_position: 6
---

# The Profile Header (Ontology Metadata)

Every CGMES / ENTSO-E profile has a metadata block that identifies it — title, identified version, keyword, licence, conformsTo, description, and so on. In RDFArchitect this is the **Profile header**, reachable from **Edit → Edit → Profile header** (the menu entry shows **View** or **Add** depending on state).

The header editor is a row-based dialog: each row is a known field (e.g. `dcat:version`, `dcterms:title`, `cim:Ontology.baseUriScheme`) with its value. A list of *known* ontology fields — the ones standardised by ENTSO-E and commonly used in CGMES profiles — can be added in one click from the **Add known fields** sub-dialog, so you don't have to remember the exact predicate names.

On export, the profile header is written as the first resource of the file — matching the convention used by ENTSO-E's official releases — and can optionally be re-generated from schema metadata at export time.
