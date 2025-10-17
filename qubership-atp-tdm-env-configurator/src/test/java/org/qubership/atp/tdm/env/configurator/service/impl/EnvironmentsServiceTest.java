///*
// * # Copyright 2024-2025 NetCracker Technology Corporation
// * #
// * # Licensed under the Apache License, Version 2.0 (the "License");
// * # you may not use this file except in compliance with the License.
// * # You may obtain a copy of the License at
// * #
// * #      http://www.apache.org/licenses/LICENSE-2.0
// * #
// * # Unless required by applicable law or agreed to in writing, software
// * # distributed under the License is distributed on an "AS IS" BASIS,
// * # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * # See the License for the specific language governing permissions and
// * # limitations under the License.
// */
//
//package org.qubership.atp.tdm.env.configurator.service.impl;
//
//import static org.qubership.atp.tdm.env.configurator.EnvironmentHelper.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.when;
//
//import java.util.Collections;
//import java.util.List;
//
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//
//import org.modelmapper.ModelMapper;
//import org.qubership.atp.tdm.env.configurator.model.Connection;
//import org.qubership.atp.tdm.env.configurator.model.LazyEnvironment;
//import org.qubership.atp.tdm.env.configurator.model.LazyProject;
//import org.qubership.atp.tdm.env.configurator.model.LazySystem;
//import org.qubership.atp.tdm.env.configurator.model.Project;
//import org.qubership.atp.tdm.env.configurator.service.CacheService;
//import org.qubership.atp.tdm.env.configurator.service.GitService;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.boot.test.mock.mockito.SpyBean;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.test.context.junit.jupiter.SpringExtension;
//
//import org.qubership.atp.tdm.env.configurator.service.DtoConvertService;
//import org.qubership.atp.tdm.env.configurator.service.EnvironmentsService;
//
//@ExtendWith(SpringExtension.class)
//public class EnvironmentsServiceTest {
//
//    private EnvironmentsService environmentsService;
//
//    @MockBean
//    protected GitService gitService;
//    @MockBean
//    protected CacheService cacheService;
//    @SpyBean
//    protected ModelMapper modelMapper;
//    @SpyBean
//    protected DtoConvertService dtoConvertService;
//
//    @BeforeEach
//    public void setUp() {
////        when(gitService.getLazyProjects()).thenReturn();
////        when(gitService.getAllShort(eq(false)))
////                .thenReturn(new ResponseEntity(Collections.singletonList(projectNameViewDto), HttpStatus.OK));
////        when(gitService.getProject(any(), eq(true)))
////                .thenReturn(new ResponseEntity(projectFullVer1ViewDto, HttpStatus.OK));
////        when(gitService.getShortProject(any(), eq(false)))
////                .thenReturn(new ResponseEntity(projectLazyVer1ViewDto, HttpStatus.OK));
////        when(gitService.getEnvironments(any(), eq(false)))
////                .thenReturn(new ResponseEntity(Collections.singletonList(environmentResDto), HttpStatus.OK));
////        when(gitService.getShortProjectByName(any(), eq(false)))
////                .thenReturn(new ResponseEntity(projectFullVer1ViewDtoLazyProjectByName, HttpStatus.OK));
////        when(gitService.getProjectSystems(any(), any(), any()))
////                .thenReturn(new ResponseEntity(systemsFullVer2ViewDtoP, HttpStatus.OK));
////        when(gitService.getAllShortSystemsOnProject(any()))
////                .thenReturn(new ResponseEntity(systemEnvironmentsViewDto, HttpStatus.OK));
////        when(gitService.getEnvironmentsShort(any()))
////                .thenReturn(new ResponseEntity(Collections.singletonList(lazyEnvironmentShort), HttpStatus.OK));
////        when(gitService.getSystemsShort(any()))
////                .thenReturn(new ResponseEntity(systemFullVer2ViewDto, HttpStatus.OK));
////        when(gitService.getEnvironment(any(), eq(true)))
////                .thenReturn(new ResponseEntity(environmentFullVer1ViewDto, HttpStatus.OK));
////        when(gitService.getEnvironment(any(), eq(false)))
////                .thenReturn(new ResponseEntity(environmentLazyVer1ViewDto, HttpStatus.OK));
////        when(gitService.findBySearchRequest(any(), any()))
////                .thenReturn(new ResponseEntity(Collections.singletonList(environmentDto), HttpStatus.OK));
////        when(gitService.getEnvironmentNameById(any()))
////                .thenReturn(new ResponseEntity("test", HttpStatus.OK));
////        when(gitService.getSystemByName(any(), any(), eq(true)))
////                .thenReturn(new ResponseEntity(systemFullVer1ViewDto, HttpStatus.OK));
////        when(gitService.getSystemByName(any(), any(), eq(false)))
////                .thenReturn(new ResponseEntity(systemFullVer1ViewDto, HttpStatus.OK));
////        when(gitService.getShortSystem(any(), eq(false)))
////                .thenReturn(new ResponseEntity(systemFullVer1ViewDto, HttpStatus.OK));
////        when(gitService.getSystem(any(), eq(true)))
////                .thenReturn(new ResponseEntity(systemFullVer1ViewDto, HttpStatus.OK));
////        when(gitService.getSystemConnections(any(), eq(false)))
////                .thenReturn(new ResponseEntity(systemConnections, HttpStatus.OK));
//
//        environmentsService = new EnvironmentsServiceImpl(gitService, cacheService);
//    }
//
//    @Test
//    public void getLazyProjectsTest() {
//        List<LazyProject> lazyProjects = environmentsService.getLazyProjects();
//        Assertions.assertEquals(Collections.singletonList(lazyProject), lazyProjects);
//    }
//
//    @Test
//    public void getFullProjectTest() {
//        Project actualProject = environmentsService.getFullProject(project.getId());
//        Assertions.assertEquals(project, actualProject);
//    }
//
//    @Test
//    public void getLazyEnvironmentsTest() {
//        List<LazyEnvironment> actualEnvironments = environmentsService.getLazyEnvironments(project.getId());
//        Assertions.assertEquals(Collections.singletonList(lazyEnvironment), actualEnvironments);
//    }
//
//    @Test
//    public void getLazySystemsTest() {
//        List<LazySystem> actualLazySystems = environmentsService.getLazySystems(environment.getId());
//        Assertions.assertEquals(lazySystems, actualLazySystems);
//        Assertions.assertEquals("ENV", actualLazySystems.get(0).getName());
//    }
//
//    @Test
//    public void getLazyProjectByNameTest() {
//        LazyProject actualLazyProject = environmentsService.getLazyProjectByName("Lazy Project Name");
//        Assertions.assertEquals(lazyProject, actualLazyProject);
//    }
//
//    @Test
//    public void getLazyEnvironmentByNameTest() {
//        LazyEnvironment actualLazyEnvironment = environmentsService.getLazyEnvironmentByName(project.getId(),
//                " Lazy Environment Name ");
//        Assertions.assertEquals(lazyEnvironment, actualLazyEnvironment);
//    }
//
//    @Test
//    public void getLazySystemByNameTest() {
//        LazySystem lazySystemByName = environmentsService.getLazySystemByName(project.getId(),
//                environment.getId(), " System Name ");
//        Assertions.assertEquals(lazySystem, lazySystemByName);
//    }
//
//    @Test
//    public void getLazySystemByProjectIdWithConnections() {
//        List<LazySystem> systems = environmentsService.getLazySystemsByProjectIdWithConnections(project.getId());
//        Assertions.assertEquals(lazySystems, systems);
//    }
//
//    @Test
//    public void getLazySystemByProjectIdWithEnvIds() {
//        List<LazySystem> systems = environmentsService.getLazySystemsByProjectWithEnvIds(project.getId());
//        Assertions.assertEquals(lazySystems, systems);
//    }
//
//    @Test
//    public void getLazySystems() {
//        List<LazySystem> systems = environmentsService.getLazySystems(environment.getId());
//        Assertions.assertEquals(lazySystems, systems);
//    }
//
//    @Test
//    public void getLazySystemById() {
//        LazySystem lazySystemById = environmentsService.getLazySystemById(lazyEnvId.getId(), system.getId());
//        Assertions.assertEquals(lazySystem, lazySystemById);
//    }
//
//
//    @Test
//    public void getLazyEnvironment() {
//        LazyEnvironment lazyEnvironmentById = environmentsService.getLazyEnvironment(environment.getId());
//        Assertions.assertEquals(lazyEnvironment, lazyEnvironmentById);
//    }
//
//    @Test
//    public void getLazyProjectById() {
//        LazyProject lazyProjectById = environmentsService.getLazyProjectById(project.getId());
//        Assertions.assertEquals(lazyProject, lazyProjectById);
//    }
//
//    @Test
//    public void getEnvironmentByEnvironmentId() {
//        LazyEnvironment environment = environmentsService.getLazyEnvironment(lazyEnvironment.getId());
//        Assertions.assertEquals(environment, lazyEnvironment);
//    }
//
//    @Test
//    public void getConnectionsBySystemId() {
//        List<Connection> connectionsSystemById = environmentsService.getConnectionsSystemById(lazyEnvId.getId(), lazySystem.getId());
//        Assertions.assertEquals(systemConnections, connectionsSystemById);
//    }
//
//    @Test
//    public void getEnvNameByEnvironmentId() {
//        String  envName = environmentsService.getEnvNameById(lazyEnvironment.getId());
//        Assertions.assertEquals(envName, "test");
//    }
//}
