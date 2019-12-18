alter table "Beneficiaries" add column "medicareBeneficiaryIdHashed" varchar(64);
alter table "BeneficiariesHistory" add column "medicareBeneficiaryIdHashed" varchar(64);

create index ${logic.index-create-concurrently} "Beneficiaries_mbi_idx"
    on "Beneficiaries" ("medicareBeneficiaryIdHashed");

create index ${logic.index-create-concurrently} "BeneficiariesHistory_mbi_idx"
    on "BeneficiariesHistory" ("medicareBeneficiaryIdHashed");