# Literature mapping

This document positions RiskGraph AI's design choices against published research.
Every entry below was independently re-verified: title, authors, venue, and year
were cross-checked against the publisher's own record (ACM Digital Library, IEEE
Xplore, Wiley, USENIX.org, arXiv.org, SciPy Proceedings, dblp/HAL) and, for the
older/foundational entries, additionally confirmed to return a matching
`scholar.google.com/scholar_lookup` entry (title + author + year identical) when
searched by exact title. Google Scholar's own pages block automated fetching
(robots.txt), so "available on Google Scholar" below was checked by exact-title
search rather than scraping the results page — the "Verification status" table at
the end of this document records exactly what was confirmed for each entry and
how, so it can be repeated by hand in a browser.

**What this is not:** a claim that RiskGraph re-implements any cited paper's
algorithm, or that the specific risk weights in [risk-scoring.md](risk-scoring.md)
(0.25 / 0.20 / 0.20 / 0.15 / 0.10 / 0.10) come from the literature — they are this
project's own engineering choice. What the papers below establish is that each
*category* of technique RiskGraph uses — static extraction, graph-based reachability
for access control, hybrid static+dynamic confirmation, LLM-as-explainer rather than
LLM-as-verdict, and multi-factor risk scoring — is an active, peer-reviewed research
direction, not an ad hoc invention.

## 1. Tools RiskGraph directly depends on

| RiskGraph component | What it does | Citation |
|---|---|---|
| `services/java-analyzer` (Spoon extraction) | Parses Java/Spring source into an AST to extract routes, `@PreAuthorize` annotations, and call paths | R. Pawlak, M. Monperrus, N. Petitprez, C. Noguera, L. Seinturier, "Spoon: A Library for Implementing Analyses and Transformations of Java Source Code," *Software: Practice and Experience*, Wiley, Vol. 46, No. 9, 2016 (online first 2015). DOI: [10.1002/spe.2346](https://onlinelibrary.wiley.com/doi/abs/10.1002/spe.2346) · open-access copy: [HAL hal-01078532](https://hal.science/hal-01078532) |
| `services/graph-risk-service` (before/after graphs, BFS reachability) | Builds the security graph and runs reachability over it | A. Hagberg, D. Schult, P. Swart, "Exploring Network Structure, Dynamics, and Function using NetworkX," *Proceedings of the 7th Python in Science Conference (SciPy2008)*, 2008. [proceedings.scipy.org/articles/TCWV9851](https://proceedings.scipy.org/articles/TCWV9851) |

These are direct dependency citations, not methodology claims — RiskGraph literally
calls these libraries.

## 2. The core problem: statically detecting missing/removed authorization checks

RiskGraph's authorization-removal scenario (an endpoint loses its `@PreAuthorize`
guard and becomes reachable from an anonymous request) is the same problem class as:

- F. Sun, L. Xu, Z. Su, "Static Detection of Access Control Vulnerabilities in Web
  Applications," *USENIX Security Symposium*, 2011.
  [usenix.org/legacy/events/sec11/tech/full_papers/Sun.pdf](https://www.usenix.org/legacy/events/sec11/tech/full_papers/Sun.pdf)
  — statically infers where access-control checks should exist from code structure
  and flags where they are missing. RiskGraph instead diffs a known-good graph
  against the changed one, which is a narrower, deterministic version of the same
  goal (detecting *regressions* in enforcement rather than inferring policy from
  scratch).
- "Detecting Broken Object-Level Authorization Vulnerabilities in Database-Backed
  Applications," *Proceedings of the 2024 ACM SIGSAC Conference on Computer and
  Communications Security (CCS)*, 2024. ACM DL:
  [10.1145/3658644.3690227](https://dl.acm.org/doi/10.1145/3658644.3690227) ·
  mirror: [ResearchGate 386574642](https://www.researchgate.net/publication/386574642_Detecting_Broken_Object-Level_Authorization_Vulnerabilities_in_Database-Backed_Applications)
  — title, venue, and DOI confirmed directly against the ACM Digital Library; the
  author list could not be independently confirmed past the paywall and is
  intentionally omitted here rather than guessed. Closely matches RiskGraph's
  `sensitive-resource-exposure` scenario (a route reaching a sensitive resource
  without adequate authorization); current top-venue evidence that this exact
  vulnerability class is an active research target.

## 3. Graph-based representation and reachability for security reasoning

RiskGraph turns extracted facts into a graph and asks a reachability question
(*"is there a path from an anonymous entry point to a sensitive resource?"*) rather
than pattern-matching source text. This is the same paradigm as:

- F. Yamaguchi, N. Golde, D. Arp, K. Rieck, "Modeling and Discovering
  Vulnerabilities with Code Property Graphs," *IEEE Symposium on Security and
  Privacy (S&P)*, 2014. DOI:
  [10.1109/SP.2014.44](https://ieeexplore.ieee.org/document/6956589/)
  — merges AST/CFG/PDG into one graph and queries it for vulnerability patterns.
  RiskGraph's graph is a purpose-built role/endpoint/service/repository/resource
  graph, not a full code property graph — narrower in scope, but the same
  "represent the program as a graph, then query it" idea.
- O. Sheyner, J. Haines, S. Jha, R. Lippmann, J. M. Wing, "Automated Generation
  and Analysis of Attack Graphs," *IEEE Symposium on Security and Privacy
  (S&P)*, 2002.
  [conferences.computer.org/sp/pdfs/sp/2002/02_08_01.pdf](https://conferences.computer.org/sp/pdfs/sp/2002/02_08_01.pdf)
  · [CMU mirror](https://www.cs.cmu.edu/~scenariograph/sheyner-wing02.pdf)
  — the foundational attack-graph paper: builds a graph of all reachable
  attack states and reasons over paths through it, the same "represent
  reachability as a graph, then query it" idea RiskGraph applies narrowly to
  anonymous-to-sensitive-resource reachability instead of a full attack
  surface.
- O. Tripp, M. Pistoia, S. Fink, M. Sridharan, O. Weisman, "TAJ: Effective Taint
  Analysis of Web Applications," *PLDI 2009*. DOI:
  [10.1145/1542476.1542486](https://dl.acm.org/doi/10.1145/1542476.1542486); see
  also S. Arzt et al., "FlowDroid," *PLDI 2014*, DOI:
  [10.1145/2594291.2594299](https://dl.acm.org/doi/10.1145/2594291.2594299)
  — the canonical source-to-sink reachability (taint analysis) literature.
  RiskGraph's BFS reachability check is a scoped instance of the same
  source-to-sink question, using authorization state instead of full data-flow
  taint as the thing being tracked.

## 4. Hybrid static + dynamic confirmation

RiskGraph never finalizes a `BLOCK` from static evidence alone when a validation
sandbox is available — an isolated Docker probe attempts to confirm or reject the
hypothesis (see [decision-policy.md](decision-policy.md)'s
preliminary/CONFIRMED/REJECTED matrix). This mirrors:

- P. Centonze, R. J. Flynn, M. Pistoia, "Combining Static and Dynamic Analysis for
  Automatic Identification of Precise Access-Control Policies," *Annual Computer
  Security Applications Conference (ACSAC)*, 2007. IEEE Xplore:
  [ieeexplore.ieee.org/document/4412997](https://ieeexplore.ieee.org/document/4412997/)
  · dblp record: [CentonzeFP07](https://dblp.uni-trier.de/rec/conf/acsac/CentonzeFP07.html)
  · conference PDF: [acsac.org/2007/papers/175.pdf](https://www.acsac.org/2007/papers/175.pdf)
  — nearly a direct match: static analysis proposes an access-control hypothesis,
  dynamic execution confirms it, precisely RiskGraph's static-risk-engine →
  Docker-sandbox-probe → final-verdict pipeline.

## 5. LLM as explainer, not as verdict

RiskGraph's Ollama layer can only explain evidence and propose a test; it cannot
create graph edges, assign risk, or confirm a finding (see
[threat-model.md](threat-model.md)). This design choice — keep the LLM
schema-constrained and advisory, keep the verdict deterministic — is studied
directly in:

- Z. Li, S. Dutta, M. Naik, "IRIS: LLM-Assisted Static Analysis for Detecting
  Security Vulnerabilities," *International Conference on Learning Representations
  (ICLR)*, 2025. arXiv:
  [2405.17238](https://arxiv.org/abs/2405.17238) ·
  [ICLR proceedings page](https://proceedings.iclr.cc/paper_files/paper/2025/hash/582d4e27fa24168f3af1f4582655034b-Abstract-Conference.html)
  · [Semantic Scholar record](https://www.semanticscholar.org/paper/LLM-Assisted-Static-Analysis-for-Detecting-Security-Li-Dutta/1ddcd76d2e4c40bbc7aa8e8f1fc77d5ecf11bcb2)
  — uses an LLM to guide/augment a static-analysis pipeline while keeping the
  underlying static analysis (not the LLM) as the source of ground truth, the same
  division of labor RiskGraph enforces between `graph-risk-service` and
  `ai-validation-service`.

## 6. Multi-factor, deterministic risk scoring

RiskGraph scores risk as a fixed weighted sum of six components
(Reachability, AuthorizationChange, DataSensitivity, ExternalExposure,
PrivilegeImpact, Exploitability) rather than a single CVSS-style number. Three
lines of published work directly support that structure, plus a fourth
industry-standard methodology with the same shape:

- OWASP, "OWASP Risk Rating Methodology" (Jeff Williams), *OWASP Foundation*.
  [owasp.org/www-community/OWASP_Risk_Rating_Methodology](https://owasp.org/www-community/OWASP_Risk_Rating_Methodology)
  — a widely used, non-academic industry methodology, cited here as a
  standard rather than a paper (same treatment as the SARIF entry below). It
  decomposes risk into weighted Likelihood factors (threat-agent skill,
  motive, opportunity; ease of discovery/exploit) and Impact factors
  (confidentiality/integrity/availability loss, business impact), each scored
  independently and combined through a Likelihood x Impact matrix into named
  severity bands. RiskGraph's own six weighted sub-scores rolling up into
  LOW/MODERATE/MEDIUM/HIGH/CRITICAL bands is the same shape: several
  independently scored factors, never one opaque number, combined by a fixed,
  published rule rather than intuition.
- J. Jacobs, S. Romanosky, B. Edwards, M. Roytman, I. Adjerid, "Exploit
  Prediction Scoring System (EPSS)," *Digital Threats: Research and
  Practice*, ACM, Vol. 2, No. 3, 2021. DOI:
  [10.1145/3436242](https://dl.acm.org/doi/10.1145/3436242) · preprint:
  [arXiv:1908.04856](https://arxiv.org/abs/1908.04856)
  — establishes exploitability as its own modeled factor, separate from raw
  severity, because whether a flaw is likely to be *exploited* is a distinct
  question from how *damaging* it would be. RiskGraph's Exploitability
  component (scored independently of DataSensitivity/PrivilegeImpact) follows
  the same separation, at a far simpler, deterministic, rule-based level
  rather than EPSS's trained probabilistic model.
- J. Homer, S. Zhang, X. Ou, D. Schmidt, Y. Du, S. R. Rajagopalan, A. Singhal,
  "Aggregating Vulnerability Metrics in Enterprise Networks Using Attack
  Graphs," *Journal of Computer Security*, Vol. 21, No. 4, 2013, pp. 561-597.
  DOI: [10.3233/JCS-130475](https://journals.sagepub.com/doi/10.3233/JCS-130475)
  — directly supports RiskGraph's core design choice: instead of scoring each
  finding in isolation, aggregate multiple metrics *through the structure of
  a reachability graph* to get one meaningful risk number. RiskGraph's
  risk-after score is exactly this — component scores rolled up over a graph
  reachability result, not computed independently of the graph.
- "A Survey on Vulnerability Prioritization: Taxonomy, Metrics, and Research
  Challenges," arXiv:
  [2502.11070](https://arxiv.org/html/2502.11070v1), 2025. Existence, title,
  and abstract confirmed directly on arXiv; the author list is omitted here
  rather than guessed, for the same reason as the CCS 2024 entry above —
  confirm it directly on the arXiv page before citing it elsewhere.

Together these support the *approach* (multi-factor, graph-aggregated,
deterministic scoring over a single opaque number); the specific six weights
RiskGraph uses (0.25/0.20/0.20/0.15/0.10/0.10) are this project's own
calibration, documented and versioned in
[risk-scoring.md](risk-scoring.md), not derived from any cited source — no
paper here prescribes those exact numbers, and none should be cited as if it
did.

## 7. Why PR-integrated evidence, not a raw scan report

RiskGraph surfaces results as a GitHub Check + SARIF + PR summary with source
locations and transparent component scores, rather than a standalone report a
developer has to go find. This is a direct response to a well-documented failure
mode of static analysis adoption:

- B. Johnson, Y. Song, E. Murphy-Hill, R. Bowdidge, "Why Don't Software Developers
  Use Static Analysis Tools to Find Bugs?," *International Conference on Software
  Engineering (ICSE)*, 2013. IEEE Xplore:
  [ieeexplore.ieee.org/document/6606613](https://ieeexplore.ieee.org/document/6606613/)
  (DOI: 10.1109/ICSE.2013.6606613) · ACM DL:
  [10.5555/2486788.2486877](https://dl.acm.org/doi/10.5555/2486788.2486877) ·
  open-access copy: [cs.gmu.edu/~johnsonb/docs/icse2013.pdf](https://cs.gmu.edu/~johnsonb/docs/icse2013.pdf)
  — empirically documents that developers distrust and ignore static analysis
  tools mainly due to false positives, poor workflow integration, and unclear
  evidence — the exact gaps RiskGraph's Check/SARIF/dashboard output, source
  locations, and confidence/coverage reporting are designed to close.

## Reporting format (standard, not a paper)

RiskGraph emits the unmodified OASIS SARIF 2.1.0 Errata 01 schema
(`contracts/reporting/`) rather than a custom report format, so results are
consumable by any SARIF-aware tool (GitHub code scanning included). This is a
standard, not academic literature:
<https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json>,
maintained by [OASIS](https://www.oasis-open.org/policies-guidelines/ipr/).

## Verification status

Every citation links directly to the publisher (ACM Digital Library, IEEE
Xplore, USENIX, SciPy Proceedings, Wiley) or to an arXiv/ICLR/HAL page for
preprints — none depends on a blog post, vendor page, or secondary summary as
its source of truth. "Scholar-confirmed" below means an exact-title search on
`scholar.google.com` returned a `scholar_lookup` result with matching title,
authors, and year; Scholar's own pages block automated fetching, so this was
done as a targeted search rather than a scrape, and is the same check anyone
can repeat by hand in a browser.

| # | Citation | Publisher record | Scholar-confirmed | Notes |
|---|---|---|---|---|
| 1 | Pawlak et al., Spoon (2016) | Wiley DOI 10.1002/spe.2346 | Yes | Author list corrected during re-verification (5th author is Seinturier, not a duplicate Noguera) |
| 2 | Hagberg et al., NetworkX (2008) | SciPy2008 proceedings | Yes | No changes needed |
| 3 | Sun, Xu, Su, USENIX Security 2011 | usenix.org PDF | Yes | Authors confirmed against the PDF itself |
| 4 | BOLA detection, ACM CCS 2024 | ACM DL DOI 10.1145/3658644.3690227 | Title/venue/DOI only | Author list not accessible past the paywall; intentionally omitted rather than guessed |
| 5 | Yamaguchi et al., Code Property Graphs, IEEE S&P 2014 | IEEE Xplore DOI 10.1109/SP.2014.44 | Yes | No changes needed |
| 6 | Tripp et al. TAJ (PLDI 2009) + Arzt et al. FlowDroid (PLDI 2014) | ACM DL DOIs | Yes (both) | No changes needed |
| 7 | Centonze, Flynn, Pistoia, ACSAC 2007 | IEEE Xplore + dblp + acsac.org PDF | Yes | Added IEEE Xplore and dblp links during re-verification |
| 8 | Li, Dutta, Naik, IRIS, ICLR 2025 | arXiv:2405.17238 + ICLR proceedings | Yes | Author names confirmed via arXiv and ML Anthology during re-verification |
| 9 | Vulnerability prioritization survey, arXiv 2502.11070 (2025) | arXiv | Title/abstract only | Author list omitted, same reasoning as #4 |
| 10 | Johnson, Song, Murphy-Hill, Bowdidge, ICSE 2013 | IEEE Xplore DOI 10.1109/ICSE.2013.6606613 + ACM DL | Yes | A DOI (10.1145/2516760.2516769) surfaced by an earlier search was checked and found to belong to an unrelated paper — not used; correct IEEE DOI substituted |
| 11 | Sheyner, Haines, Jha, Lippmann, Wing, Attack Graphs, IEEE S&P 2002 | IEEE conference PDF + CMU mirror | Yes | Foundational, highly cited; added during rule-set enhancement pass |
| 12 | Jacobs, Romanosky, Edwards, Roytman, Adjerid, EPSS, ACM DTRAP 2021 | ACM DL DOI 10.1145/3436242 + arXiv:1908.04856 | Yes | Journal venue/volume/issue confirmed via ACM search index (DL page itself returned 403 to automated fetch) |
| 13 | Homer, Zhang, Ou, Schmidt, Du, Rajagopalan, Singhal, Attack-Graph Metric Aggregation, J. Computer Security 2013 | SAGE/IOS Press DOI 10.3233/JCS-130475 | Yes | Full author list confirmed directly against the publisher record |
| — | OWASP Risk Rating Methodology (Jeff Williams) | owasp.org (OWASP Foundation) | N/A (standard, not a paper) | Cited as an industry methodology, same treatment as SARIF |
| — | OASIS SARIF 2.1.0 Errata 01 | docs.oasis-open.org | N/A (standard, not a paper) | Cited as a standard throughout |

To spot-check any entry: search the exact title in Google Scholar and confirm
venue, year, and DOI match this table.
