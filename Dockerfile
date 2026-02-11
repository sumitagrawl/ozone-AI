# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM apache/ozone-runner:20250625-2-jdk21

######UCX support#######
# 1. build the image
#docker build -t ozone-runner
# 2. update below conf to pickup image in ".env" file in ozone compose, like hadoop-ozone/dist/target/compose/ozone/.env 
#OZONE_RUNNER_VERSION=latest
#OZONE_RUNNER_IMAGE=ozone-runner
USER root

RUN sudo dnf install -y 'dnf-command(config-manager)' && \
    dnf config-manager --set-enabled crb && \
    dnf group install -y "Development Tools" && \
    dnf install -y \
        git \
        wget \
        pkgconf-pkg-config \
        numactl-devel \
        rdma-core \
        libibverbs-utils \
        iproute \
        kmod \
        libibverbs-devel \
        librdmacm-devel && \
    dnf clean all

# Build UCX from source with RDMA/Verbs support
WORKDIR /opt
RUN wget https://github.com/openucx/ucx/releases/download/v1.19.1/ucx-1.19.1.tar.gz \
    && tar -xvf ucx-1.19.1.tar.gz \
    && cd ucx-1.19.1 \
    && ./configure --prefix=/usr/local --enable-optimizations --with-verbs --with-rdmacm  \
    && make -j$(nproc) \
    && make install
ENV LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH

USER hadoop
###########END UCX SUPPORT

ADD --chown=hadoop . /opt/hadoop

WORKDIR /opt/hadoop
