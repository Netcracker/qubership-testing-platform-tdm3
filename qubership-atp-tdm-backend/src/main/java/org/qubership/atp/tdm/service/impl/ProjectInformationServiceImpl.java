/*
 *  Copyright 2024-2025 NetCracker Technology Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.qubership.atp.tdm.service.impl;

import org.qubership.atp.tdm.model.ProjectInformation;
import org.qubership.atp.tdm.repo.ProjectInformationRepository;
import org.qubership.atp.tdm.service.ProjectInformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProjectInformationServiceImpl implements ProjectInformationService {

    private final ProjectInformationRepository projectInformationRepository;

    @Autowired
    public ProjectInformationServiceImpl(ProjectInformationRepository projectInformationRepository) {
        this.projectInformationRepository = projectInformationRepository;
    }

    @Override
    public void saveProjectInformation(ProjectInformation projectInformation) {
        projectInformationRepository.save(projectInformation);
    }
}
