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
--

alter table "Beneficiaries" add column lastUpdated timestamp with time zone;

alter table "BeneficiariesHistory" add column lastUpdated timestamp with time zone;

alter table "MedicareBeneficiaryIdHistory" add column lastUpdated timestamp with time zone;

alter table "PartDEvents" add column lastUpdated timestamp with time zone;

alter table "CarrierClaims" add column lastUpdated timestamp with time zone;

alter table "InpatientClaims" add column lastUpdated timestamp with time zone;

alter table "OutpatientClaims" add column lastUpdated timestamp with time zone;

alter table "HHAClaims" add column lastUpdated timestamp with time zone;

alter table "DMEClaims" add column lastUpdated timestamp with time zone;

alter table "HospiceClaims" add column lastUpdated timestamp with time zone;

alter table "SNFClaims" add column lastUpdated timestamp with time zone;

-- 
-- Add tables that track the ETL process
--

-- One row for each batch of RIF files. The timestamps represent start and end time of processing the .
--
create table "Batches" (
	"batchId" bigint primary key,							-- Internal db key
	"fileCount"	int not null, 								-- The number of RIF files in this batch
	"firstUpdated" timestamp with time zone,				-- The timestamp before processing the first RIF file of this batch
	"lastUpdated" timestamp with time zone					-- The timestamp after processing the last RIF file of this batch
)

create sequence batches_batchId_seq ${logic.sequence-start} 1 ${logic.sequence-increment} 10;

-- One row for each beneficiary updated in a batch
create table "BatchBeneficiaries" (
	"batchId" bigint not null,								-- One set per batch
	"beneficiaryId" varchar(15) not null,					-- The beneficiaries in the batch file
	primary key ("batchId", "beneficiaryId")
)
;

alter table "BatchBeneficiaries" 				
	add constraint "batchBeneficiaries_batchId" 
		foreign key ("batchId") 
		references " ";

