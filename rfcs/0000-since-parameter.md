# RFC Proposal
[RFC Proposal]: #rfc-proposal

* RFC Proposal ID: `0000-since-parameter-support` 
* Start Date: October 1, 2019
* RFC PR: <https://github.com/CMSgov/beneficiary-fhir-data/pull/85>
* JIRA Ticket(s): 
    - [BlueButton-1506: Bulk Export Since Support](https://jira.cms.gov/browse/BLUEBUTTON-1506)


This RFC proposal adds features to BFD's API to allow BFD's partners to implement the Bulk Export `_since` parameter. Specifically, it provides for a `lastUpdated` query parameter to FHIR resource search operation and a feed of metadata about BFD's data loads. The proposal discusses these new features as well as the logic that BFD's partners need to implement the `_since` parameter correctly. 

## Table of Contents

* [RFC Proposal](#rfc-proposal)
* [Table of Contents](#table-of-contents)
* [Motivation](#motivation)
* [Proposed Solution](#proposed-solution)
    * [BFD API Details](#bfd-api-details)
    * [BFD Feed Details](#bfd-feed-details)
    * [Since Implementors Details](#since-implementors-details)
    * [ETL Corner Case](#etl-corner-case)
    * [Roster Change Corner Case](#roster-change-corner-case)
    * [Internal Database Corner Case](#internal-database-corner-cases)
    * [Replication Lag Corner Case](#replication-lag-corner-case)
    * [Alternatives Considered](#alternatives-considered)
* [Future Possibilities](#future-possibilities)
* [Addendums](#addendums)

## Motivation
[Motivation]: #motivation

Consumers of CMS's beneficiary data APIs whether they call BlueButton 2.0, BCDA or DPC's want the most up-to-date information. Ideally, these apps and services would like to call a CMS API right as CMS updates its claim information. When they do call, they only want new data from CMS, not the information they already have. 

FHIR \[[1](#ref1)\] has provisions for an "update me about new information" pattern in FHIR APIs. For the bulk export operation, exports with a `_since` parameter specified should only return resources that have changed after the date and time specified in the `_since` parameter. For synchronous resource searches, there exists a `lastUpdated` parameter that has similar semantics. 

Today, BFD only supports returning all EOB resources associated with a single beneficiary. EOB calls return more than 5 years of beneficiary data, where only the last weeks of data is needed. This behavior is highly inefficient for the bulk export calls that happen weekly. On average, each call is returning 260 times as much information as is needed. 

Early feedback from both BCDA and DPC customers have nearly unanimously pointed out the need for _since parameter support \[[2](#ref2)\]. For BCDA, where an export operation can take many hours and result in 100's GB of data, BCDA customers have stated that they need '_since' support to move to production. BB 2.0 app developers would like a similar feature as well. 

## Proposed Solution

This proposal adds 3 changes to the BFD API that are needed for downstream partners to implement the `_since` parameter. 

1. The `lastUpdated` metadata field of EOB, Patient, and Coverage FHIR resources contains the time they were written to the master DB by the ETL process. 
2. The search operation of the EOB, Patient, and Coverage resources support a `_lastUpdated` query parameter. When specified, the search filters resources against the passed in date range. The capabilities statement includes the `_lastUpdated` as a search parameter. 
3. The BFD server adds optimizations on EOB searches with `_lastUpdated` for the case where the result set is empty. These searches should return results in a similar time to the time taken by a metadata query. 

### BFD API Details

All the proposed API changes follow the FHIR specification. 

The first improvement is to add the `lastUpdated` field to the metadata object of a resource. The current implementation does not return any `lastUpdated` field. The proposal adds this field with the timestamp that the ETL process wrote to the master DB. Like all FHIR date fields, this timestamp must include the server's timezone \[[4](#ref4)\]. Resources based on records loaded before this RFC is implemented continue to not have `lastUpdated` fields. 

The second change is to support the `_lastUpdated` query parameter for resource searches per the FHIR specification \[[5](#ref5)\]. FHIR specifies a set of comparison operators to go along with this filter. BFD supports the `eq`, `lt`, `le`, `gt` and `ge` operators. Two `_lastUpdated` parameters can be specified to form the upper and lower bounds of a time interval. Searches with a `_lastUpdated` parameter will not return resources without a `lastUpdated` field. To retrive these resources, a query without `_lastUpdated` must be made. 

### Bulk Export Implementors Details

Implementing `_since` support should be straight forward for BFD's partners that implement the FHIR Bulk Export specification. The following sequence diagram shows how  interaction should work. 

![Bulk ](https://www.websequencediagrams.com/files/render?link=zfMUJyQaf18DNUb6IQoPN2EBeq9tMctYXXupx6T5Co8gB3t9ysmhat0ToalxZ6p2)

The partner 

### BFD Implementation Details

![Filters](https://www.lucidchart.com/publicSegments/view/e3f43d21-5fdc-403c-b366-eec09e7db10d/image.png)

### ETL Corner Case

Every export job completion record contains a `transactionTime` field that bulk-export clients use as the `_since` parameter in a subsequent export job. The `transactionTime` is the time that the export job starts. The FHIR bulk export specification states that an export SHALL only contain resources updated before the `transactionTime` time. The specification further says that an exporter should delay a job until all pending writes have finished to satisfy this constraint. The ELT feed allows bulk-export implementors to know when the BFD ETL finishes. Since both the bulk export operation and the BFD ETL process take several hours, the delay may be significant. 

To avoid this delay, a bulk-export implementor may take an optimistic approach by immediately starting an export job, but monitoring if it receives a resource updated after the job start time. If it does, the it should delay and restart the job.  

### Roster Change Corner Case

The resources returned by a group export operation is the current roster of the group at the time of an export call. A group's roster may change between successive export calls. At this time, the importer does not have any data for the added beneficiaries. So, how should an export call with a `_since` parameter handle new beneficiaries? The FHIR specification states that export should only include data updated after the passed in `_since` parameter. However, the specification does not contemplate this use-case, nor does it offer any hint on how to correctly implement this use-case. 

Since the BFD service does not track groups, the BFD partners have to work out solutions for this problem. The FHIR community Please see the authors for a discussion on solutions. 

### Internal Database Corner Cases

FHIR Resources are projections from the BFD's internal records which are based the CCW's RIF files.  As a result, the FHIR Resources may have their `lastUpdated` field change when other fields do not change. 

Records created before the since feature was implmented, do not have a defined `lastUpdated` value. In this case, the BFD will return a default value. 

### Alternatives Considered

Instead of optimizing ETL feed served by S3, BFD could convey the ETL information in other ways. For example, BFD could add an API for this information. Given the low volume of events and the low number of subscribers, the proposal chooses the S3 approach because it requires less code, while still meeting the needs of the problem

## Future Possibilities

This proposal should scale as BFD, and it's partners serve more beneficiaries and clients. It should continue to work as BFD adds more partners and data sources. 

In future releases, BFD may receive claim data faster than the current delay. If the ETL process runs daily instead of weekly as it does today, the algorithms in this proposal should continue to work. If one day, the BFD receives claim data continuously, we should revisit the algorithms of this proposal. 

In discussions with DPC customers, they have asked for notification when the DPC has new beneficiary data. Instead of polling for updates, they would like to have the ability for a push update.  Similarly, FHIR is developing a subscription model that supports webhooks \[[6](#ref6)\]. If a BFD partner wants to develop these features, they can use the ETL feed as a source of information for this feature. 

## References

The following references are required to fully understand and implement this proposal. They should be read before voting on this proposal.

<a id="ref1"></a>
[1] FHIR - Fast Health Interoperability Resources: <https://www.fhir.org>

<a id="ref2"></a>
[2] Rick Hawes: Conversations with customers of DPC and BCDA

<a id="ref3"></a>
[3] Working copy of the Bulk Export specification: <https://build.fhir.org/ig/HL7/bulk-data/export/index.html>

<a id="ref4"></a>
[4] FHIR Meta.lastUpdated definition: <https://www.hl7.org/fhir/resource-definitions.html#meta.lastupdated> 

<a id="ref5"></a>
[5] FHIR Search operation: <https://www.hl7.org/fhir/search.html>   

<a id="ref6"></a>
[6] FHIR Subscriptions: <https://www.hl7.org/fhir/subscription.html>

<a id="ref7"></a>
[7] Original Confluence page with an implementation outline: <https://confluence.cms.gov/pages/viewpage.action?pageId=189269516>