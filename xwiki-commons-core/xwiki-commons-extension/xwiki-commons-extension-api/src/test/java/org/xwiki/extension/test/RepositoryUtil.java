/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.extension.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.xwiki.component.annotation.ComponentAnnotationLoader;
import org.xwiki.component.descriptor.ComponentDescriptor;
import org.xwiki.component.descriptor.DefaultComponentDescriptor;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.environment.Environment;
import org.xwiki.extension.handler.ExtensionInitializer;
import org.xwiki.extension.repository.CoreExtensionRepository;
import org.xwiki.extension.repository.ExtensionRepositoryId;
import org.xwiki.extension.repository.ExtensionRepositoryManager;
import org.xwiki.extension.version.internal.DefaultVersion;

public class RepositoryUtil
{
    private static final String MAVENREPOSITORY_ID = "test-maven";

    private File permanentDirectory;

    private File temporaryDirectory;

    private File extensionDirectory;

    private File localRepositoryRoot;

    private File mavenRepositoryRoot;

    private File remoteRepositoryRoot;

    private FileExtensionRepository remoteRepository;

    private ComponentManager componentManager;

    private ExtensionPackager extensionPackager;

    private ComponentAnnotationLoader componentLoader;

    public RepositoryUtil(ComponentManager componentManager)
    {
        this.componentManager = componentManager;

        File testDirectory = new File("target/test-" + new Date().getTime());

        this.temporaryDirectory = new File(testDirectory, "temporary-dir");

        this.permanentDirectory = new File(testDirectory, "permanent-dir");
        this.extensionDirectory = new File(this.permanentDirectory, "extension/");
        this.localRepositoryRoot = new File(this.extensionDirectory, "repository/");

        this.mavenRepositoryRoot = new File(testDirectory, "maven/");
        this.remoteRepositoryRoot = new File(testDirectory, "remote/");

        this.extensionPackager = new ExtensionPackager(this.permanentDirectory, this.remoteRepositoryRoot);
    }

    public File getPermanentDirectory()
    {
        return this.permanentDirectory;
    }

    public File getTemporaryDirectory()
    {
        return this.temporaryDirectory;
    }

    public File getExtensionDirectory()
    {
        return this.extensionDirectory;
    }

    public File getLocalRepository()
    {
        return this.localRepositoryRoot;
    }

    public File getRemoteRepository()
    {
        return this.remoteRepositoryRoot;
    }

    public File getMavenRepository()
    {
        return this.mavenRepositoryRoot;
    }

    public String getRemoteRepositoryId()
    {
        return MAVENREPOSITORY_ID;
    }

    public void setup(Mockery mockery) throws Exception
    {
        // Mock Environment
        final Environment environment = mockery.mock(Environment.class);
        mockery.checking(new Expectations()
        {
            {
                allowing(environment).getPermanentDirectory();
                will(returnValue(getPermanentDirectory()));
                allowing(environment).getTemporaryDirectory();
                will(returnValue(getTemporaryDirectory()));
            }
        });
        DefaultComponentDescriptor<Environment> dcd = new DefaultComponentDescriptor<Environment>();
        dcd.setRoleType(Environment.class);
        this.componentManager.registerComponent(dcd, environment);

        // add default test core extension

        registerComponent(ConfigurableDefaultCoreExtensionRepository.class);
        ((ConfigurableDefaultCoreExtensionRepository) this.componentManager
            .getInstance(CoreExtensionRepository.class)).addExtensions("coreextension", new DefaultVersion(
            "version"));

        // copy

        copyResourceFolder(getLocalRepository(), "repository.local");

        // remote repositories

        ExtensionRepositoryManager repositoryManager =
            this.componentManager.getInstance(ExtensionRepositoryManager.class);

        // light remote repository

        if (copyResourceFolder(getRemoteRepository(), "repository.remote") > 0) {
            this.remoteRepository = new FileExtensionRepository(getRemoteRepository(), this.componentManager);
            repositoryManager.addRepository(remoteRepository);
        }

        // maven resource repository

        if (copyResourceFolder(getMavenRepository(), "repository.maven") > 0) {
            repositoryManager.addRepository(new ExtensionRepositoryId(MAVENREPOSITORY_ID, "maven", getMavenRepository()
                .toURI()));
        }

        // generated extensions

        this.extensionPackager.generateExtensions();

        // init

        this.componentManager.<ExtensionInitializer>getInstance(ExtensionInitializer.class).initialize();
    }

    public ComponentAnnotationLoader getComponentLoader()
    {
        if (this.componentLoader == null) {
            this.componentLoader = new ComponentAnnotationLoader();
        }

        return this.componentLoader;
    }

    private void registerComponent(Class< ? > componentClass) throws Exception
    {
        List<ComponentDescriptor> descriptors = getComponentLoader().getComponentsDescriptors(componentClass);

        for (ComponentDescriptor< ? > descriptor : descriptors) {
            this.componentManager.registerComponent(descriptor);
        }
    }

    public int copyResourceFolder(File targetFolder, String resourcePackage) throws IOException
    {
        int nb = 0;

        targetFolder.mkdirs();

        Set<URL> urls = ClasspathHelper.forPackage(resourcePackage);

        if (!urls.isEmpty()) {
            Reflections reflections =
                new Reflections(new ConfigurationBuilder().setScanners(new ResourcesScanner()).setUrls(urls)
                    .filterInputsBy(new FilterBuilder.Include(FilterBuilder.prefix(resourcePackage))));

            for (String resource : reflections.getResources(Pattern.compile(".*"))) {
                File targetFile = new File(targetFolder, resource.substring(resourcePackage.length() + 1));

                InputStream resourceStream = getClass().getResourceAsStream("/" + resource);

                try {
                    FileUtils.copyInputStreamToFile(resourceStream, targetFile);
                    ++nb;
                } finally {
                    resourceStream.close();
                }
            }
        }

        return nb;
    }
}
