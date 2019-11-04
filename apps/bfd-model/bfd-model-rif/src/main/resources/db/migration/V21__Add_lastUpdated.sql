-- Add a column without writing a data to every row
--
-- From the PostgreSQL 9.6 ALTER command documentation...
--
-- When a column is added with ADD COLUMN, all existing rows in the table are initialized with the
-- column's default value (NULL if no DEFAULT clause is specified). If there is no DEFAULT clause, this is merely a
-- metadata change and does not require any immediate update of the table's data; the added NULL values are supplied
-- on readout, instead.
--
-- Based on this information, alters have the implicit default null

alter table "Beneficiaries" add column "lastUpdated" timestamp with time zone;

alter table "BeneficiariesHistory" add column "lastUpdated" timestamp with time zone;

alter table "MedicareBeneficiaryIdHistory" add column "lastUpdated" timestamp with time zone;

alter table "PartDEvents" add column "lastUpdated" timestamp with time zone;

alter table "CarrierClaims" add column "lastUpdated" timestamp with time zone;

alter table "InpatientClaims" add column "lastUpdated" timestamp with time zone;

alter table "OutpatientClaims" add column "lastUpdated" timestamp with time zone;

alter table "HHAClaims" add column "lastUpdated" timestamp with time zone;

alter table "DMEClaims" add column "lastUpdated" timestamp with time zone;

alter table "HospiceClaims" add column "lastUpdated" timestamp with time zone;

alter table "SNFClaims" add column "lastUpdated" timestamp with time zone;

-- 
-- Add tables that track the RIF load process
--

-- One row for each RIF file processed. The timestamps represent start and end time of processing the file. 
create table "RifFiles" (
	"rifFileId" bigint primary key,							-- Internal db key
	"rifType" varchar(48) not null,							-- The RifFileType 
	"sequenceId" varchar(16) not null,						-- Sequence from the manifest file
	"startTime" timestamp with time zone,					-- Timestamp from the pipeline process	
	"endTime" timestamp with time zone						-- Timestamp from the pipeline process
)

create sequence rifFiles_id_seq ${logic.sequence-start} 1 ${logic.sequence-increment} 10;

-- One row for each beneficiary updated per each RIF file processed 
create table "RifFileBeneficiaries" (
	"rifFileId" bigint not null,							-- One set per RifFile
	"beneficiaryId" varchar(15) not null,					-- The beneficiaries in the rif file
	primary key ("rifFileId", "beneficiaryId")
)
;

alter table "RifFileBeneficiaries" 				
	add constraint "rifFileBeneficiaries_rifFilesId_to_id" 
		foreign key ("rifFileId") 
		references "RifFiles";

