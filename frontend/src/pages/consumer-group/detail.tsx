import type { ConsumerGroupMember } from '@/interfaces/user';
import PermissionGuard from '@/components/PermissionGuard';
import {
  addGroupMembers,
  getConsumerGroup,
  listGroupGrants,
  listGroupMembers,
  removeGroupMember,
  updateGroupGrants,
} from '@/services/consumer-group';
import { listUsers } from '@/services/user';
import { PageContainer } from '@ant-design/pro-layout';
import { useRequest } from 'ahooks';
import { Button, Card, Descriptions, message, Modal, Select, Space, Table, Tabs, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useState } from 'react';
import { useParams } from 'ice';
import { useTranslation } from 'react-i18next';

const { TabPane } = Tabs;

const ConsumerGroupDetail: React.FC = () => {
  const { t } = useTranslation();
  const params = useParams<{ id: string }>();
  const groupId = Number(params.id);

  const [group, setGroup] = useState<any>(null);
  const [members, setMembers] = useState<Array<ConsumerGroupMember & { displayName?: string; employeeId?: string }>>([]);
  const [grants, setGrants] = useState<any[]>([]);
  const [addMemberVisible, setAddMemberVisible] = useState(false);
  const [selectedUsernames, setSelectedUsernames] = useState<string[]>([]);
  const [allUsers, setAllUsers] = useState<any[]>([]);

  const { loading: groupLoading, refresh: refreshGroup } = useRequest(
    () => getConsumerGroup(groupId),
    {
      manual: true,
      onSuccess: (result) => setGroup(result),
    },
  );

  const { loading: membersLoading, refresh: refreshMembers } = useRequest(
    () => listGroupMembers(groupId),
    {
      manual: true,
      onSuccess: (result) => {
        const memberList = (result || []) as ConsumerGroupMember[];
        // Enrich with user info
        allUsers.forEach((user: any) => {
          const member = memberList.find((m) => m.username === user.name);
          if (member) {
            (member as any).displayName = user.displayName;
            (member as any).employeeId = user.employeeId;
          }
        });
        setMembers(memberList);
      },
    },
  );

  const { loading: grantsLoading, refresh: refreshGrants } = useRequest(
    () => listGroupGrants(groupId),
    {
      manual: true,
      onSuccess: (result) => setGrants(result || []),
    },
  );

  const { loading: usersLoading } = useRequest(listUsers, {
    manual: true,
    onSuccess: (result) => {
      const users = (result || []) as any[];
      setAllUsers(users);
    },
  });

  useEffect(() => {
    if (groupId) {
      refreshGroup();
      refreshMembers();
      refreshGrants();
    }
  }, [groupId]);

  useEffect(() => {
    // Reload members after users are loaded
    if (allUsers.length > 0 && groupId) {
      refreshMembers();
    }
  }, [allUsers]);

  const handleAddMembers = async () => {
    if (selectedUsernames.length === 0) return;
    try {
      await addGroupMembers(groupId, selectedUsernames);
      message.success(t('consumerGroup.addMemberSuccess'));
      setAddMemberVisible(false);
      setSelectedUsernames([]);
      refreshMembers();
    } catch (e) {
      // handled by interceptor
    }
  };

  const handleRemoveMember = async (username: string) => {
    try {
      await removeGroupMember(groupId, username);
      message.success(t('consumerGroup.removeMemberSuccess'));
      refreshMembers();
    } catch (e) {
      // handled by interceptor
    }
  };

  const memberColumns: ColumnsType<any> = [
    {
      title: t('consumerGroup.memberUsername'),
      dataIndex: 'username',
      key: 'username',
    },
    {
      title: t('consumerGroup.memberDisplayName'),
      dataIndex: 'displayName',
      key: 'displayName',
      render: (val: string) => val || '-',
    },
    {
      title: t('consumerGroup.memberEmployeeId'),
      dataIndex: 'employeeId',
      key: 'employeeId',
      render: (val: string) => val || '-',
    },
    {
      title: t('misc.actions'),
      key: 'action',
      width: 100,
      align: 'center',
      render: (_: any, record: any) => (
        <PermissionGuard allowedRoles={['platform_admin', 'owner', 'manager']}>
          <a onClick={() => handleRemoveMember(record.username)}>{t('consumerGroup.removeMember')}</a>
        </PermissionGuard>
      ),
    },
  ];

  const grantColumns: ColumnsType<any> = [
    {
      title: t('consumerGroup.grantResourceType'),
      dataIndex: 'resourceType',
      key: 'resourceType',
      render: (val: string) => <Tag>{val}</Tag>,
    },
    {
      title: t('consumerGroup.grantResourceName'),
      dataIndex: 'resourceName',
      key: 'resourceName',
    },
  ];

  const availableUsers = allUsers.filter(
    (u: any) => !members.some((m) => m.username === u.name),
  );

  return (
    <PageContainer
      title={group ? `${group.nameCn} (${group.shortName})` : ''}
      onBack={() => history?.back?.()}
    >
      <Tabs defaultActiveKey="info">
        <TabPane tab={t('consumerGroup.tabInfo')} key="info">
          <Card loading={groupLoading}>
            <Descriptions column={2}>
              <Descriptions.Item label={t('consumerGroup.nameCn')}>{group?.nameCn}</Descriptions.Item>
              <Descriptions.Item label={t('consumerGroup.nameEn')}>{group?.nameEn}</Descriptions.Item>
              <Descriptions.Item label={t('consumerGroup.shortName')}>{group?.shortName}</Descriptions.Item>
              <Descriptions.Item label={t('consumerGroup.status')}>
                {group?.status === 'active' ? t('consumerGroup.statusActive') : t('consumerGroup.statusDisabled')}
              </Descriptions.Item>
              <Descriptions.Item label={t('consumerGroup.description')} span={2}>
                {group?.description || '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </TabPane>

        <TabPane tab={t('consumerGroup.tabMembers')} key="members">
          <Card
            extra={
              <PermissionGuard allowedRoles={['platform_admin', 'owner', 'manager']}>
                <Button type="primary" onClick={() => setAddMemberVisible(true)}>
                  {t('consumerGroup.addMember')}
                </Button>
              </PermissionGuard>
            }
          >
            <Table
              loading={membersLoading}
              dataSource={members}
              columns={memberColumns}
              rowKey="id"
              pagination={false}
            />
          </Card>
        </TabPane>

        <TabPane tab={t('consumerGroup.tabGrants')} key="grants">
          <Card>
            <Table
              loading={grantsLoading}
              dataSource={grants}
              columns={grantColumns}
              rowKey="id"
              pagination={false}
            />
          </Card>
        </TabPane>
      </Tabs>

      <Modal
        title={t('consumerGroup.addMember')}
        open={addMemberVisible}
        onOk={handleAddMembers}
        onCancel={() => {
          setAddMemberVisible(false);
          setSelectedUsernames([]);
        }}
        okText={t('misc.confirm')}
        cancelText={t('misc.cancel')}
      >
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder={t('consumerGroup.selectUsers')}
          value={selectedUsernames}
          onChange={setSelectedUsernames}
          options={availableUsers.map((u: any) => ({
            label: `${u.displayName || u.name} (${u.name})`,
            value: u.name,
          }))}
        />
      </Modal>
    </PageContainer>
  );
};

export default ConsumerGroupDetail;
