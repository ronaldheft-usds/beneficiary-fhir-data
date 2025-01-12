#!/bin/bash
set -e

exec > >(tee -a /var/log/user_data.log 2>&1)

# Extend gold image defined root partition with all available free space
sudo growpart /dev/nvme0n1 2
sudo pvresize /dev/nvme0n1p2
sudo lvextend -l +100%FREE /dev/VolGroup00/rootVol
sudo xfs_growfs /

git clone https://github.com/CMSgov/beneficiary-fhir-data.git --branch ${gitBranchName} --single-branch

cd beneficiary-fhir-data/ops/ansible/playbooks-ccs/

git checkout ${gitCommitId}

aws s3 cp s3://bfd-mgmt-admin-${accountId}/ansible/vault.password .

# The extra_vars.json file from the previous build step contains a few incorrect values
# and needs to get trimmed down to the following
cat <<EOF >> extra_vars.json
{
    "env":"${env}",
    "data_pipeline_jar":"/bluebutton-data-pipeline/bfd-pipeline-app-1.0.0-SNAPSHOT-capsule-fat.jar"
}
EOF

ansible-playbook --extra-vars '@extra_vars.json' --vault-password-file=vault.password --tags "post-ami" launch_bfd-pipeline.yml

rm vault.password